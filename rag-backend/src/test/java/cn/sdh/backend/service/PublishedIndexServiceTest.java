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
        jdbc.execute("CREATE TABLE evaluation_task(id BIGINT PRIMARY KEY)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V24__knowledge_publishing_pipeline.sql"),new ClassPathResource("db/migration/V38__published_index_generations.sql"),new ClassPathResource("db/migration/V39__index_read_leases_and_cleanup.sql"),new ClassPathResource("db/migration/V40__index_publication_quality_gate.sql"),new ClassPathResource("db/migration/V42__historical_evaluation_replay.sql")).execute(source);
        service = new PublishedIndexService(jdbc, new DataSourceTransactionManager(source),new IndexGenerationLifecycle(jdbc,new DataSourceTransactionManager(source),new cn.sdh.backend.config.IndexLifecycleProperties()),new IndexPublicationGateStore(jdbc,org.mockito.Mockito.mock(AgentImprovementService.class)));
        context = org.mockito.Mockito.mock(cn.sdh.backend.job.PlatformJobExecutionContext.class);
        org.mockito.Mockito.when(context.jobId()).thenReturn(12L);
    }
    @AfterEach void close() { source.destroy(); }
    @Test void historicalEvaluationReopensItsRecordedGenerationAfterTheHeadChanges() {
        next(2,12); var first=service.stage(payload(2),12L,"b".repeat(64),context);
        service.publish(payload(2),12L,first.key(),1,context);
        String recorded;
        try(var old=service.openEvaluation(7L)) { recorded=old.snapshot().version(); }
        next(3,12); var second=service.stage(payload(3),12L,"c".repeat(64),context);
        service.publish(payload(3),12L,second.key(),1,context);
        try(var replay=service.openHistorical(7L,recorded)) {
            assertEquals(recorded,replay.snapshot().version());
            assertEquals(first.key(),replay.generations().getFirst().key());
        }
        try(var live=service.open(7L)) { assertEquals(second.key(),live.generations().getFirst().key()); }
    }
    @Test void queueReservationsProtectHistoryAndRollbackWithTaskCreation() {
        next(2,12); var first=service.stage(payload(2),12L,"b".repeat(64),context); service.publish(payload(2),12L,first.key(),1,context);
        String version; try(var view=service.open(7L)) { version=view.snapshot().version(); }
        assertThrows(IllegalStateException.class,() -> service.reserveHistorical(7L,version,"a".repeat(32),() -> { throw new IllegalStateException("injected queue failure"); }));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_index_snapshot_hold",Integer.class));
        assertEquals("queued",service.reserveHistorical(7L,version,"b".repeat(32),() -> "queued"));
        next(3,12); var second=service.stage(payload(3),12L,"c".repeat(64),context); service.publish(payload(3),12L,second.key(),1,context);
        var policy=new cn.sdh.backend.config.IndexLifecycleProperties(); policy.setRetentionSeconds(0);
        var cleanup=new IndexGenerationLifecycle(jdbc,new DataSourceTransactionManager(source),policy);
        assertTrue(cleanup.claim(7L).stream().noneMatch(c -> c.generation().key().equals(first.key())));
        jdbc.update("UPDATE knowledge_index_snapshot_hold SET expires_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP)");
        assertTrue(cleanup.claim(7L).stream().anyMatch(c -> c.generation().key().equals(first.key())));
        assertThrows(BusinessException.class,() -> service.openHistorical(7L,version));
    }
    @Test void legacyDeletedRevokedAndCrossKnowledgeHistoryCannotBeReopened() {
        String legacy; try(var view=service.open(7L)) { legacy=view.snapshot().version(); }
        assertThrows(BusinessException.class,() -> service.openHistorical(7L,legacy));
        next(2,12); var first=service.stage(payload(2),12L,"b".repeat(64),context); service.publish(payload(2),12L,first.key(),1,context);
        String version; try(var view=service.open(7L)) { version=view.snapshot().version(); }
        assertThrows(BusinessException.class,() -> service.openHistorical(8L,version));
        jdbc.update("UPDATE knowledge_index_generation SET state='DELETED' WHERE generation_key=?",first.key());
        assertThrows(BusinessException.class,() -> service.openHistorical(7L,version));
        jdbc.update("UPDATE knowledge_index_generation SET state='PUBLISHED' WHERE generation_key=?",first.key());
        managedDraft(); jdbc.update("UPDATE knowledge_draft SET status='ROLLED_BACK' WHERE id=31");
        assertThrows(BusinessException.class,() -> service.openHistorical(7L,version));
        assertThrows(IllegalStateException.class,() -> service.current(7L));
    }
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
    @Test void aManagedDraftMustPassItsGateBeforeItsIndexBecomesVisible() {
        managedDraft();
        next(2,12);
        assertThrows(BusinessException.class,() -> {
            var stage=managedStage();
            service.publish(payload(2),12L,stage.key(),3,context);
        },"Index completion alone must not publish an unvalidated business draft");
        assertEquals("legacy:1:1",jdbc.queryForObject("SELECT published_generation FROM knowledge_document_relation WHERE id=1",String.class));
    }
    @Test void isolatedCandidatePassesOnlyForItsPhysicalGenerationAndPublishesAtomically() {
        managedDraft(); next(2,12); var stage=managedStage();
        var vectors=org.mockito.Mockito.mock(VectorStoreService.class);
        org.mockito.Mockito.when(vectors.candidateSearch("question",7L,5,"model")).thenAnswer(call -> {
            assertTrue(service.current(7L).filter("document_id").toString().contains(stage.key()));
            var hit=new org.springframework.ai.document.Document("candidate",Map.of("document_id",42L,"index_generation",stage.key()));
            return List.of(hit);
        });
        var gate=new IndexPublicationGate(service,new IndexPublicationGateStore(jdbc,org.mockito.Mockito.mock(AgentImprovementService.class)),vectors);
        assertTrue(gate.evaluate(payload(2),12L,stage.key(),1,"model",context));
        assertEquals("legacy:1:1",jdbc.queryForObject("SELECT published_generation FROM knowledge_document_relation WHERE id=1",String.class));
        assertTrue(service.publish(payload(2),12L,stage.key(),1,context));
        assertEquals("PUBLISHED",jdbc.queryForObject("SELECT status FROM knowledge_draft WHERE id=31",String.class));
        assertEquals(stage.key(),jdbc.queryForObject("SELECT published_generation FROM knowledge_draft WHERE id=31",String.class));
        assertEquals("PASSED",jdbc.queryForObject("SELECT status FROM knowledge_index_publication_gate",String.class));
        assertTrue(service.publish(payload(2),12L,stage.key(),1,context),"A repeated publication must remain idempotent");
    }
    @Test void anOlderDocumentHitCannotPassTheCandidateGate() {
        managedDraft(); next(2,12); var stage=managedStage();
        var vectors=org.mockito.Mockito.mock(VectorStoreService.class);
        org.mockito.Mockito.when(vectors.candidateSearch("question",7L,5,"model")).thenReturn(List.of(new org.springframework.ai.document.Document("old",Map.of("document_id",42L,"index_generation","old-batch"))));
        var gate=new IndexPublicationGate(service,new IndexPublicationGateStore(jdbc,org.mockito.Mockito.mock(AgentImprovementService.class)),vectors);
        assertFalse(gate.evaluate(payload(2),12L,stage.key(),1,"model",context));
        assertEquals("GATE_FAILED",jdbc.queryForObject("SELECT status FROM knowledge_draft WHERE id=31",String.class));
        assertEquals("legacy:1:1",jdbc.queryForObject("SELECT published_generation FROM knowledge_document_relation WHERE id=1",String.class));
        assertThrows(BusinessException.class,() -> service.publish(payload(2),12L,stage.key(),1,context));
    }
    @Test void changedApprovedBytesOrChangedRetrievalBasisCannotBePublished() {
        managedDraft(); next(2,12);
        assertThrows(BusinessException.class,() -> service.stage(payload(2),12L,"b".repeat(64),"f".repeat(64),context));
        var stage=managedStage();
        try(var view=service.openCandidate(payload(2),12L,stage.key(),context)) {
            service.saveGateEvaluation(payload(2),12L,stage.key(),view.basisVersion(),view.snapshot().version(),new KnowledgePublishingService.ValidationResult(true,List.of(new KnowledgePublishingService.ValidationItem("question",true,1))),context);
        }
        jdbc.update("INSERT INTO knowledge_document_relation(id,knowledge_id,document_id,index_revision,published_generation) VALUES(2,7,99,1,'other')");
        jdbc.update("INSERT INTO knowledge_index_generation(generation_key,relation_id,knowledge_id,document_id,index_revision,state) VALUES('other',2,7,99,1,'PUBLISHED')");
        assertThrows(BusinessException.class,() -> service.publish(payload(2),12L,stage.key(),1,context));
    }
    @Test void technicalHeadsWithoutBusinessAdmissionAreInvisible() {
        managedDraft();
        jdbc.update("UPDATE knowledge_draft SET published_generation=NULL WHERE id=31");
        try(var view=service.open(7L)) { assertEquals(0,view.snapshot().generationCount()); assertTrue(view.filter("document_id").isMatchNone()); }
    }
    @Test void infrastructureFailureDoesNotBecomeAFailedQualityVerdict() {
        managedDraft(); next(2,12); var stage=managedStage();
        var vectors=org.mockito.Mockito.mock(VectorStoreService.class);
        org.mockito.Mockito.when(vectors.candidateSearch("question",7L,5,"model")).thenThrow(new IllegalStateException("search unavailable"));
        var gate=new IndexPublicationGate(service,new IndexPublicationGateStore(jdbc,org.mockito.Mockito.mock(AgentImprovementService.class)),vectors);
        assertThrows(IllegalStateException.class,() -> gate.evaluate(payload(2),12L,stage.key(),1,"model",context));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM knowledge_index_publication_gate",String.class));
        assertEquals("PUBLISHING",jdbc.queryForObject("SELECT status FROM knowledge_draft WHERE id=31",String.class));
    }
    @Test void malformedQueriesAndCrossKnowledgeLinksCannotBypassTheGate() {
        managedDraft(); next(2,12);
        for(String invalid:List.of("[]","broken","[1]")) {
            jdbc.update("UPDATE knowledge_draft SET regression_queries=? WHERE id=31",invalid);
            assertThrows(BusinessException.class,this::managedStage);
        }
        jdbc.update("UPDATE knowledge_draft SET regression_queries='[\"question\"]' WHERE id=31");
        jdbc.update("INSERT INTO knowledge_document_relation(id,knowledge_id,document_id,index_revision,job_id,process_status) VALUES(2,8,42,1,12,1)");
        var config=new DocumentLinkConfig(); config.setEmbeddingModel("model");
        var other=new DocumentIndexJobPayload(2L,42L,8L,1,config);
        assertThrows(BusinessException.class,() -> service.stage(other,12L,"b".repeat(64),"f".repeat(64),context));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_index_generation",Integer.class));
    }
    private void managedDraft() {
        jdbc.execute("CREATE TABLE knowledge_document(id BIGINT PRIMARY KEY,status INT)");
        jdbc.update("INSERT INTO knowledge_document VALUES(42,0)");
        jdbc.update("INSERT INTO knowledge_draft(id,knowledge_id,title,content,status,regression_queries,published_document_id,content_checksum,created_by,reviewed_by,reviewed_at,created_at,updated_at) VALUES(31,7,'approved',?,'PUBLISHING','[\"question\"]',42,'checksum',7,7,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)","approved content ".repeat(10));
        jdbc.update("UPDATE knowledge_draft SET published_generation='legacy:1:1' WHERE id=31");
    }
    private PublishedIndexService.Stage managedStage() {
        String raw=cn.sdh.backend.utils.ContentFingerprint.sha256(KnowledgePublicationContent.markdown("approved","approved content ".repeat(10)));
        return service.stage(payload(2),12L,"b".repeat(64),raw,context);
    }
    void next(int revision, long job) { jdbc.update("UPDATE knowledge_document_relation SET index_revision=?,job_id=?,process_status=1,index_source_sha256=NULL,index_write_generation=NULL WHERE id=1", revision, job); }
    static DocumentIndexJobPayload payload(int revision) { var config=new DocumentLinkConfig(); config.setEmbeddingModel("model"); return new DocumentIndexJobPayload(1L, 42L, 7L, revision,config); }
}
