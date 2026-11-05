package cn.sdh.backend.service;

import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PublishedIndexLeaseTest {
    SingleConnectionDataSource source;
    JdbcTemplate jdbc;
    PublishedIndexService publications;
    IndexGenerationLifecycle lifecycle;
    cn.sdh.backend.config.IndexLifecycleProperties policy;
    @BeforeEach void setup() {
        source = new SingleConnectionDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "", true);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE knowledge_document_relation(id BIGINT PRIMARY KEY,knowledge_id BIGINT,document_id BIGINT,index_revision INT,job_id BIGINT,process_status INT,chunk_count INT,error_message VARCHAR(2000),process_time TIMESTAMP,create_time TIMESTAMP)");
        jdbc.update("INSERT INTO knowledge_document_relation VALUES(1,7,42,1,11,2,1,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE evaluation_task(id BIGINT PRIMARY KEY)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V24__knowledge_publishing_pipeline.sql"),new ClassPathResource("db/migration/V38__published_index_generations.sql"),new ClassPathResource("db/migration/V39__index_read_leases_and_cleanup.sql"),new ClassPathResource("db/migration/V40__index_publication_quality_gate.sql"),new ClassPathResource("db/migration/V42__historical_evaluation_replay.sql")).execute(source);
        policy=new cn.sdh.backend.config.IndexLifecycleProperties(); policy.setRetentionSeconds(0);
        lifecycle=new IndexGenerationLifecycle(jdbc,new DataSourceTransactionManager(source),policy);
        publications = new PublishedIndexService(jdbc,new DataSourceTransactionManager(source),lifecycle,new IndexPublicationGateStore(jdbc,org.mockito.Mockito.mock(AgentImprovementService.class)));
    }
    @AfterEach void close() { source.destroy(); }
    @Test void nestedViewsShareOneDurableLeaseAndCloseReleasesIt() {
        try(var view = publications.open(7L)) {
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_index_read_lease",Integer.class));
            assertEquals("legacy:1:1",jdbc.queryForObject("SELECT generation_key FROM knowledge_index_read_snapshot_generation",String.class));
            try(var nested = publications.open(7L)) { assertEquals(view.snapshot(),nested.snapshot()); }
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_index_read_lease",Integer.class));
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_index_read_snapshot_generation",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_index_read_lease",Integer.class));
        try(var again=publications.open(7L)) {
            assertEquals(1,again.snapshot().generationCount());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_index_read_snapshot_generation",Integer.class),"A repeated reader must reuse the immutable reference set");
        }
    }
    @Test void anExpiredViewCannotConstructOrReturnAQueryResult() {
        try(var view = publications.open(7L)) {
            jdbc.update("UPDATE knowledge_index_read_lease SET expires_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP)");
            assertThrows(cn.sdh.backend.common.exception.IndexReadLeaseExpiredException.class,() -> view.filter("document_id"));
            assertThrows(cn.sdh.backend.common.exception.IndexReadLeaseExpiredException.class,view::snapshot);
        }
    }
    @Test void aReaderProtectsARetiredGenerationUntilItsLeaseIsClosed() {
        try(var old=publications.open(7L)) {
            jdbc.update("UPDATE knowledge_document_relation SET published_generation=NULL WHERE id=1");
            assertTrue(lifecycle.claim(7L).isEmpty());
            assertEquals(1,old.snapshot().generationCount());
        }
        var claims=lifecycle.claim(7L);
        assertEquals(1,claims.size()); assertEquals("legacy:1:1",claims.getFirst().generation().key());
        assertTrue(lifecycle.finish(claims.getFirst(),true));
        assertEquals("DELETED",jdbc.queryForObject("SELECT state FROM knowledge_index_generation",String.class));
    }
    @Test void currentHeadAndCurrentWriterNeverBecomeCleanupCandidates() {
        jdbc.update("INSERT INTO knowledge_index_generation(generation_key,relation_id,knowledge_id,document_id,index_revision,state) VALUES('writing',1,7,42,2,'STAGING')");
        jdbc.update("UPDATE knowledge_document_relation SET index_write_generation='writing' WHERE id=1");
        assertTrue(lifecycle.claim(7L).isEmpty());
        assertTrue(lifecycle.dueKnowledgeBases().isEmpty());
    }
    @Test void expiredReadersAndCleanupWorkersDoNotBlockRecovery() {
        try(var old=publications.open(7L)) {
            jdbc.update("UPDATE knowledge_document_relation SET published_generation=NULL WHERE id=1");
            jdbc.update("UPDATE knowledge_index_read_lease SET expires_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP)");
            var first=lifecycle.claim(7L).getFirst();
            assertThrows(cn.sdh.backend.common.exception.IndexReadLeaseExpiredException.class,old::snapshot);
            assertTrue(lifecycle.claim(7L).isEmpty());
            jdbc.update("UPDATE knowledge_index_generation SET cleanup_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP)");
            var next=lifecycle.claim(7L).getFirst();
            assertFalse(lifecycle.finish(first,true));
            assertTrue(lifecycle.finish(next,false));
            assertTrue(lifecycle.claim(7L).isEmpty(),"Failed deletions must back off");
            jdbc.update("UPDATE knowledge_index_generation SET next_cleanup_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP)");
            assertEquals(1,lifecycle.claim(7L).size());
        }
    }
    @Test void retentionStartsWhenSupersededAndDeletedTombstonesAreReswept() {
        policy.setRetentionSeconds(60);
        jdbc.update("UPDATE knowledge_document_relation SET published_generation=NULL WHERE id=1");
        assertTrue(lifecycle.claim(7L).isEmpty());
        jdbc.update("UPDATE knowledge_index_generation SET next_cleanup_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP)");
        var first=lifecycle.claim(7L).getFirst(); assertTrue(lifecycle.finish(first,true));
        assertTrue(lifecycle.claim(7L).isEmpty());
        jdbc.update("UPDATE knowledge_index_generation SET next_cleanup_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP)");
        assertEquals(first.generation(),lifecycle.claim(7L).getFirst().generation());
    }
}
