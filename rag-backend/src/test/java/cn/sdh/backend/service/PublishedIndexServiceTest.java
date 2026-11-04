package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.dto.DocumentLinkConfig;
import cn.sdh.backend.job.DocumentIndexJobPayload;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class PublishedIndexServiceTest {
    SingleConnectionDataSource source;
    JdbcTemplate jdbc;
    PublishedIndexService service;
    cn.sdh.backend.job.PlatformJobExecutionContext context;
    @BeforeEach void setup() {
        source = new SingleConnectionDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "", true);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE knowledge_document_relation(id BIGINT PRIMARY KEY,knowledge_id BIGINT,document_id BIGINT,index_revision INT,job_id BIGINT,process_status INT,chunk_count INT,error_message VARCHAR(2000),process_time TIMESTAMP,create_time TIMESTAMP)");
        jdbc.update("INSERT INTO knowledge_document_relation VALUES(1,7,42,1,11,2,1,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V38__published_index_generations.sql"),new ClassPathResource("db/migration/V39__index_read_leases_and_cleanup.sql")).execute(source);
        service = new PublishedIndexService(jdbc, new DataSourceTransactionManager(source),new IndexGenerationLifecycle(jdbc,new DataSourceTransactionManager(source),new cn.sdh.backend.config.IndexLifecycleProperties()));
        context = org.mockito.Mockito.mock(cn.sdh.backend.job.PlatformJobExecutionContext.class);
        org.mockito.Mockito.when(context.jobId()).thenReturn(12L);
    }
    @AfterEach void close() { source.destroy(); }
    @Test void stagingIsInvisibleAndAReadViewDoesNotChangeAcrossPublication() {
        try (var old = service.open(7L)) {
            assertEquals(1, old.snapshot().legacyCount());
            String version = old.snapshot().version();
            next(2, 12); var stage = service.stage(payload(2), 12L, "b".repeat(64), context);
            assertEquals(version, service.current(7L).snapshot().version());
            assertTrue(service.publish(payload(2), 12L, stage.key(), 3, context));
            assertEquals(version, service.current(7L).snapshot().version());
            try (var nested = service.open(7L)) { assertEquals(version, nested.snapshot().version()); }
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_index_generation", Integer.class));
        }
        try (var current = service.open(7L)) {
            assertEquals(0, current.snapshot().legacyCount());
            assertTrue(current.filter("document_id").toString().contains("g:1:2:"));
            assertFalse(current.filter("document_id").toString().contains("legacy"));
        }
    }
    @Test void obsoleteJobsAndChangedSourcesCannotPublish() {
        next(2, 12); var stage = service.stage(payload(2), 12L, "b".repeat(64), context);
        assertEquals(409, assertThrows(BusinessException.class, () -> service.stage(payload(2), 12L, "c".repeat(64), context)).getCode());
        next(3, 13);
        assertEquals(409, assertThrows(BusinessException.class, () -> service.publish(payload(2), 12L, stage.key(), 3, context)).getCode());
        assertEquals("legacy:1:1", jdbc.queryForObject("SELECT published_generation FROM knowledge_document_relation WHERE id=1", String.class));
        assertEquals("STAGING", jdbc.queryForObject("SELECT state FROM knowledge_index_generation WHERE generation_key=?", String.class, stage.key()));
    }
    @Test void retriesNeverWriteIntoTheSamePhysicalGeneration() {
        next(2, 12);
        var first = service.stage(payload(2), 12L, "b".repeat(64), context);
        var retry = service.stage(payload(2), 12L, "b".repeat(64), context);
        assertNotEquals(first.key(), retry.key(), "An expired writer must not be able to overwrite the retry's vectors");
        assertThrows(BusinessException.class, () -> service.publish(payload(2), 12L, first.key(), 1, context));
        assertTrue(service.publish(payload(2), 12L, retry.key(), 1, context));
    }
    @Test void noPublishedHeadMeansMatchNoneAndNoScopeCannotFallBackToAnUnfilteredQuery() {
        try (var empty = service.open(99L)) { assertTrue(empty.filter("document_id").isMatchNone()); }
        assertThrows(IllegalStateException.class, () -> service.current(99L));
        try (var old = service.open(7L)) {
            assertTrue(old.filter("doc_id").isMatchNone(), "An unversioned summary is not trustworthy for a pinned view");
        }
    }
    @Test void aPublishedGenerationIsIdempotentAndCannotChangeItsCount() {
        next(2, 12); var stage = service.stage(payload(2), 12L, "b".repeat(64), context); service.publish(payload(2), 12L, stage.key(), 3, context);
        assertTrue(service.stage(payload(2), 12L, "b".repeat(64), context).published());
        assertThrows(BusinessException.class, () -> service.publish(payload(2), 12L, stage.key(), 4, context));
        assertEquals(3, jdbc.queryForObject("SELECT chunk_count FROM knowledge_index_generation WHERE generation_key=?", Integer.class, stage.key()));
    }
    @Test void deletionTombstonesCannotBeRepublished() {
        next(2,12); var stage=service.stage(payload(2),12L,"b".repeat(64),context);
        jdbc.update("UPDATE knowledge_index_generation SET state='DELETING' WHERE generation_key=?",stage.key());
        assertEquals(409,assertThrows(BusinessException.class,() -> service.publish(payload(2),12L,stage.key(),1,context)).getCode());
        assertEquals("legacy:1:1",jdbc.queryForObject("SELECT published_generation FROM knowledge_document_relation WHERE id=1",String.class));
    }
    void next(int revision, long job) { jdbc.update("UPDATE knowledge_document_relation SET index_revision=?,job_id=?,process_status=1,index_source_sha256=NULL,index_write_generation=NULL WHERE id=1", revision, job); }
    static DocumentIndexJobPayload payload(int revision) { return new DocumentIndexJobPayload(1L, 42L, 7L, revision, new DocumentLinkConfig()); }
}
