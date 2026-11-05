package cn.sdh.backend.integration;

import cn.sdh.backend.agent.*;
import cn.sdh.backend.agent.runtime.JdbcAgentStateStore;
import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.mapper.*;
import cn.sdh.backend.service.*;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.state.AgentState;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in production-dialect acceptance: real MySQL, child JVM termination and actual loopback HTTP. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentMysqlIT {
    private ManagedMysql mysql;
    private String schema;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private RunExecutionLeaseService leases;
    private RunStateService runs;
    private AgentActionService actions;
    private final List<Process> children = new ArrayList<>();

    @BeforeAll void start() throws Exception {
        mysql = new ManagedMysql();
        schema = "sdh_agent_it_" + UUID.randomUUID().toString().replace("-", "");
        mysql.admin().execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        var source = mysql.source(schema);
        Flyway.configure().dataSource(source).target("30").load().migrate();
        var upgraded = Flyway.configure().dataSource(source).target("39").load();
        assertEquals(9, upgraded.migrate().migrationsExecuted, "V30 -> V39 must apply exactly nine migrations");
        var beforeGate=new JdbcTemplate(source);
        beforeGate.update("INSERT INTO knowledge_base(id,name,user_id) VALUES(900001,'legacy-gate',1)");
        var statuses=List.of("PUBLISHING","GATE_FAILED","PUBLISH_FAILED","ROLLED_BACK","PUBLISHED");
        for(int i=0;i<statuses.size();i++) {
            long id=900001L+i;
            beforeGate.update("INSERT INTO knowledge_document(id,title,user_id) VALUES(?,'legacy',1)",id);
            beforeGate.update("INSERT INTO knowledge_draft(id,knowledge_id,title,content,status,published_document_id,content_checksum,created_by,created_at,updated_at) VALUES(?,900001,'legacy','legacy',?,?,'legacy',1,NOW(),NOW())",id,statuses.get(i),id);
            beforeGate.update("INSERT INTO knowledge_document_relation(id,knowledge_id,document_id,index_revision,process_status,published_generation) VALUES(?,900001,?,1,2,?)",id,id,"legacy-gate-"+id);
            beforeGate.update("INSERT INTO knowledge_index_generation(generation_key,relation_id,knowledge_id,document_id,index_revision,state) VALUES(?,?,900001,?,1,'PUBLISHED')","legacy-gate-"+id,id,id);
        }
        upgraded=Flyway.configure().dataSource(source).target("40").load();
        assertEquals(1,upgraded.migrate().migrationsExecuted);
        for(int i=0;i<statuses.size();i++) {
            long id=900001L+i;
            assertEquals(i==4?"legacy-gate-"+id:null,beforeGate.queryForObject("SELECT published_generation FROM knowledge_document_relation WHERE id=?",String.class,id));
            assertEquals(i==4?2:i==0?0:3,beforeGate.queryForObject("SELECT process_status FROM knowledge_document_relation WHERE id=?",Integer.class,id));
        }
        upgraded=Flyway.configure().dataSource(source).target("41").load();
        assertEquals(1,upgraded.migrate().migrationsExecuted);
        upgraded=Flyway.configure().dataSource(source).target("42").load();
        assertEquals(1,upgraded.migrate().migrationsExecuted);
        upgraded.validate();
        assertEquals(0, upgraded.migrate().migrationsExecuted, "Re-running the migrator must be a no-op");
        context = MysqlAgentContext.open(source);
        jdbc = context.getBean(JdbcTemplate.class);
        leases = context.getBean(RunExecutionLeaseService.class);
        runs = context.getBean(RunStateService.class);
        actions = context.getBean(AgentActionService.class);
        jdbc.execute("CREATE TABLE it_business_receipt(id BIGINT AUTO_INCREMENT PRIMARY KEY, payload TEXT NOT NULL)");
    }

    @BeforeEach void reset() {
        for (String table : List.of("evaluation_replay_request", "knowledge_index_snapshot_hold", "evaluation_execution_attempt", "evaluation_qa", "evaluation_task", "knowledge_index_publication_gate", "knowledge_draft", "knowledge_index_read_lease", "knowledge_index_read_snapshot", "knowledge_index_generation", "knowledge_document_relation", "agent_evaluation_record", "agent_evaluation_dataset", "agent_execution_attempt", "agent_step", "agent_action", "agent_run", "agent_runtime_state", "chat_history", "platform_job", "it_business_receipt")) {
            jdbc.execute("DELETE FROM " + table); // Only this freshly created, private schema is connected.
        }
    }
    @AfterEach void stopChildren() throws Exception {
        UserContext.clear();
        for (Process child : children) kill(child);
        children.clear();
    }
    @AfterAll void stop() {
        if (context != null) context.close();
        if (mysql != null) mysql.close();
    }

    @Test void migrationsAreValidatedOnMySqlAndReachV42() {
        assertTrue(jdbc.queryForObject("SELECT VERSION()", String.class).startsWith("8."));
        assertEquals("42", jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1", String.class));
        assertEquals(42, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class));
        assertEquals(0, count("agent_run"));
    }

    private record EvaluationFixture(EvaluationTask task,cn.sdh.backend.job.PlatformJobExecutionContext worker) {}
    private EvaluationFixture evaluationFixture(String owner) {
        var task=new EvaluationTask(); task.setKnowledgeId(900001L); task.setUserId(1L); task.setTaskName("fixed-execution");
        task.setQaCount(2); task.setStatus(0); task.setEvaluationMode("RETRIEVAL"); task.setTopK(5); task.setDatasetType("imported");
        context.getBean(EvaluationTaskMapper.class).insert(task);
        var jobs=context.getBean(PlatformJobService.class);
        var job=jobs.enqueue(new PlatformJobService.EnqueueCommand("evaluation:"+task.getId(),"RAG_EVALUATION","EVALUATION",task.getId(),"{}",1,3,1L));
        assertEquals(1,jobs.claimAvailable(owner,100).size()); task.setJobId(job.getId());
        context.getBean(EvaluationTaskMapper.class).updateById(task);
        return new EvaluationFixture(task,new cn.sdh.backend.job.PlatformJobExecutionContext(job.getId(),owner,jobs));
    }
    private EvaluationQa evaluationQa(EvaluationTask task,String question) {
        var qa=new EvaluationQa(); qa.setTaskId(task.getId()); qa.setQuestion(question); qa.setExpectedAnswer("answer");
        qa.setHit(false); qa.setDocHit(false); qa.setIsNegative(false); qa.setSourceType("imported");
        qa.setRetrievedContext("private retrieved context"); return qa;
    }
    private EvaluationFixture replaySource() {
        jdbc.update("UPDATE knowledge_base SET vector_top_k=3 WHERE id=900001");
        jdbc.update("INSERT INTO knowledge_document_relation(id,knowledge_id,document_id,index_revision,published_generation) VALUES(900001,900001,900001,1,'historical-v1')");
        jdbc.update("INSERT INTO knowledge_index_generation(generation_key,relation_id,knowledge_id,document_id,index_revision,state,source_sha256) VALUES('historical-v1',900001,900001,900001,1,'PUBLISHED',?)","b".repeat(64));
        var source=evaluationFixture("replay-source");
        var item=new cn.sdh.backend.dto.EvaluationQaItem(); item.setQuestion("historical question"); item.setExpectedAnswer("answer");
        item.setExpectedDocumentIds(List.of(900001L)); item.setRelevanceJudgments(Map.of(900001L,3));
        try(var attempt=context.getBean(EvaluationExecutionService.class).start(source.task(),source.worker(),Map.of())) {
            attempt.inputs(List.of(item)); attempt.record(evaluationQa(source.task(),item.getQuestion()));
            source.task().setRecallAtK(java.math.BigDecimal.ONE); source.task().setMrr(java.math.BigDecimal.ONE); attempt.complete();
        }
        jdbc.update("INSERT INTO knowledge_index_generation(generation_key,relation_id,knowledge_id,document_id,index_revision,state,source_sha256) VALUES('historical-v2',900001,900001,900001,2,'PUBLISHED',?)","c".repeat(64));
        jdbc.update("UPDATE knowledge_document_relation SET published_generation='historical-v2',index_revision=2 WHERE id=900001");
        jdbc.update("UPDATE knowledge_base SET vector_top_k=99 WHERE id=900001");
        return source;
    }
    @Test void historicalReplayIsIdempotentAndActuallyRunsTheFrozenDatasetAndCorpus() throws Exception {
        var source=replaySource(); var replays=context.getBean(EvaluationReplayService.class);
        var command=new EvaluationReplayService.Command(UUID.randomUUID().toString(),null);
        var ids=new java.util.concurrent.CopyOnWriteArrayList<Long>();
        simultaneous(() -> { ids.add(replays.create(source.task().getCurrentExecutionId(),1L,command).getId()); return 1; });
        assertEquals(1,new HashSet<>(ids).size());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_replay_request",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_index_snapshot_hold",Integer.class));
        assertThrows(BusinessException.class,() -> replays.create(source.task().getCurrentExecutionId(),1L,new EvaluationReplayService.Command(command.requestKey(),"different-judge")));
        var policy=new cn.sdh.backend.config.IndexLifecycleProperties(); policy.setRetentionSeconds(0);
        var cleanup=new IndexGenerationLifecycle(jdbc,context.getBean(org.springframework.transaction.PlatformTransactionManager.class),policy);
        assertTrue(cleanup.claim(900001L).isEmpty(),"The queued replay must protect the old generation before a read lease exists");
        var task=context.getBean(EvaluationTaskMapper.class).selectById(ids.getFirst());
        var jobs=context.getBean(PlatformJobService.class); assertEquals(1,jobs.claimAvailable("historical-worker",100).size());
        var worker=new cn.sdh.backend.job.PlatformJobExecutionContext(task.getJobId(),"historical-worker",jobs);
        var rag=org.mockito.Mockito.mock(RagSearchService.class);
        var publications=context.getBean(PublishedIndexService.class);
        org.mockito.Mockito.when(rag.search(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(KnowledgeBase.class),org.mockito.ArgumentMatchers.isNull())).thenAnswer(call -> {
            assertEquals("historical question",call.getArgument(0));
            assertEquals(3,((KnowledgeBase)call.getArgument(1)).getVectorTopK());
            assertEquals("historical-v1",publications.current(900001L).generations().getFirst().key());
            var result=new RagSearchService.RagSearchResult();
            result.setDocuments(List.of(new org.springframework.ai.document.Document("old corpus",Map.of("id","old-chunk","document_id",900001L)))); return result;
        });
        var runtime=new cn.sdh.backend.service.impl.EvaluationServiceImpl(context.getBean(EvaluationTaskMapper.class),context.getBean(EvaluationQaMapper.class),context.getBean(VectorStoreService.class),org.mockito.Mockito.mock(KnowledgeBaseService.class),rag,org.mockito.Mockito.mock(ChatService.class),org.mockito.Mockito.mock(TestDatasetService.class),context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class),context.getBean(KnowledgeAccessService.class),jobs,context.getBean(EvaluationExecutionService.class));
        var handler=new cn.sdh.backend.job.EvaluationJobHandler(runtime,context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class));
        assertTrue(handler.execute(jobs.get(task.getJobId()),worker).contains("1"));
        var completed=context.getBean(EvaluationTaskMapper.class).selectById(task.getId());
        assertEquals(2,completed.getStatus()); assertEquals("PASSED",completed.getRegressionStatus());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_index_snapshot_hold",Integer.class));
        assertEquals(jdbc.queryForObject("SELECT input_sha256 FROM evaluation_execution_attempt WHERE id=?",String.class,source.task().getCurrentExecutionId()),jdbc.queryForObject("SELECT input_sha256 FROM evaluation_execution_attempt WHERE id=?",String.class,completed.getCurrentExecutionId()));
        try(var latest=publications.open(900001L)) { assertEquals("historical-v2",latest.generations().getFirst().key()); }
    }
    @Test void historicalReplayRejectsUnavailableInputsAndQueueFailuresLeaveNoPartialTask() {
        var source=replaySource(); var replays=context.getBean(EvaluationReplayService.class);
        String execution=source.task().getCurrentExecutionId();
        assertThrows(BusinessException.class,() -> replays.create(execution,2L,new EvaluationReplayService.Command(UUID.randomUUID().toString(),null)));
        jdbc.execute("CREATE TRIGGER it_reject_replay_job BEFORE INSERT ON platform_job FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected replay queue failure'");
        try { assertThrows(org.springframework.dao.DataAccessException.class,() -> replays.create(execution,1L,new EvaluationReplayService.Command(UUID.randomUUID().toString(),null))); }
        finally { jdbc.execute("DROP TRIGGER it_reject_replay_job"); }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_replay_request",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_index_snapshot_hold",Integer.class));
        var replay=replays.create(execution,1L,new EvaluationReplayService.Command(UUID.randomUUID().toString(),null));
        jdbc.update("UPDATE evaluation_replay_request SET expires_at=TIMESTAMPADD(SECOND,-1,NOW()) WHERE task_id=?",replay.getId());
        assertThrows(BusinessException.class,() -> replays.spec(replay));
        var jobs=context.getBean(PlatformJobService.class); assertEquals(1,jobs.claimAvailable("expired-replay",100).size());
        var worker=new cn.sdh.backend.job.PlatformJobExecutionContext(replay.getJobId(),"expired-replay",jobs);
        var rag=org.mockito.Mockito.mock(RagSearchService.class);
        var runtime=new cn.sdh.backend.service.impl.EvaluationServiceImpl(context.getBean(EvaluationTaskMapper.class),context.getBean(EvaluationQaMapper.class),context.getBean(VectorStoreService.class),org.mockito.Mockito.mock(KnowledgeBaseService.class),rag,org.mockito.Mockito.mock(ChatService.class),org.mockito.Mockito.mock(TestDatasetService.class),context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class),context.getBean(KnowledgeAccessService.class),jobs,context.getBean(EvaluationExecutionService.class));
        var handler=new cn.sdh.backend.job.EvaluationJobHandler(runtime,context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class));
        assertThrows(cn.sdh.backend.common.exception.EvaluationIntegrityException.class,() -> handler.execute(jobs.get(replay.getJobId()),worker));
        assertEquals(3,context.getBean(EvaluationTaskMapper.class).selectById(replay.getId()).getStatus(),"Preflight failures must not leave the task pending forever");
        org.mockito.Mockito.verifyNoInteractions(rag);
        jdbc.update("UPDATE evaluation_replay_request SET expires_at=TIMESTAMPADD(HOUR,24,NOW()) WHERE task_id=?",replay.getId());
        var access=context.getBean(KnowledgeAccessService.class);
        org.mockito.Mockito.doThrow(new BusinessException(403,"revoked")).when(access).requireEdit(900001L,1L);
        try { assertThrows(BusinessException.class,() -> replays.spec(replay)); }
        finally { org.mockito.Mockito.doAnswer(call -> context.getBean(KnowledgeBaseMapper.class).selectById((Long)call.getArgument(0))).when(access).requireEdit(900001L,1L); }
        jdbc.update("UPDATE knowledge_index_generation SET state='DELETED' WHERE generation_key='historical-v1'");
        assertThrows(BusinessException.class,() -> replays.create(execution,1L,new EvaluationReplayService.Command(UUID.randomUUID().toString(),null)));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task",Integer.class));
    }
    @Test void incompleteAttemptsCannotCompleteAndRetriesKeepImmutableResults() {
        var fixture=evaluationFixture("evaluation-owner"); var task=fixture.task();
        var service=context.getBean(EvaluationExecutionService.class);
        String previous;
        try(var attempt=service.start(task,fixture.worker(),Map.of("answer","v1"))) {
            previous=task.getCurrentExecutionId(); attempt.inputs(List.of("q1","q2"));
            attempt.record(evaluationQa(task,"q1"));
            assertThrows(cn.sdh.backend.common.exception.EvaluationIntegrityException.class,attempt::complete);
        }
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM evaluation_execution_attempt WHERE id=?",String.class,previous));
        String archived=jdbc.queryForObject("SELECT snapshot_json FROM evaluation_execution_result WHERE execution_id=?",String.class,previous);
        assertFalse(archived.contains("private retrieved context")); assertTrue(archived.contains("retrievedContextSha256"));
        try(var attempt=service.start(task,fixture.worker(),Map.of("answer","v1"))) {
            assertNotEquals(previous,task.getCurrentExecutionId()); attempt.inputs(List.of("q1","q2"));
            attempt.record(evaluationQa(task,"q1")); attempt.record(evaluationQa(task,"q2")); attempt.complete();
        }
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_qa WHERE task_id=?",Integer.class,task.getId()));
        assertEquals(archived,jdbc.queryForObject("SELECT snapshot_json FROM evaluation_execution_result WHERE execution_id=?",String.class,previous));
        assertEquals(2,service.attempts(task.getId(),1L).size());
        assertEquals(1,service.results(previous,1L,0,10).size());
        assertThrows(BusinessException.class,() -> service.results(previous,2L,0,10));
        assertTrue(service.alreadyCompleted(task,fixture.worker()),"Recovery after domain commit must not call the provider twice");
        var manifest=com.alibaba.fastjson2.JSON.parseObject(jdbc.queryForObject("SELECT final_snapshot_json FROM evaluation_execution_attempt WHERE id=?",String.class,task.getCurrentExecutionId()));
        assertEquals(2,manifest.getJSONArray("resultDigests").size()); assertNotNull(manifest.getString("inputSha256"));
        assertFalse(ModelExecutionScope.active());
    }
    @Test void archiveAndLegacyResultWritesRollbackTogetherAndAnOldLeaseCannotCommit() {
        var fixture=evaluationFixture("evaluation-old"); var task=fixture.task();
        var service=context.getBean(EvaluationExecutionService.class); String old;
        try(var attempt=service.start(task,fixture.worker(),Map.of())) {
            old=task.getCurrentExecutionId(); attempt.inputs(List.of("q1"));
            jdbc.execute("CREATE TRIGGER it_reject_evaluation_archive BEFORE INSERT ON evaluation_execution_result FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected archive failure'");
            try { assertThrows(org.springframework.dao.DataAccessException.class,() -> attempt.record(evaluationQa(task,"q1"))); }
            finally { jdbc.execute("DROP TRIGGER it_reject_evaluation_archive"); }
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_qa WHERE task_id=?",Integer.class,task.getId()));
            jdbc.update("UPDATE platform_job SET lease_until=TIMESTAMPADD(SECOND,-1,NOW()) WHERE id=?",task.getJobId());
            assertEquals(1,context.getBean(PlatformJobService.class).claimAvailable("evaluation-new",100).size());
            assertThrows(IllegalStateException.class,() -> attempt.record(evaluationQa(task,"stale")));
            assertThrows(IllegalStateException.class,attempt::complete);
        }
        var next=new cn.sdh.backend.job.PlatformJobExecutionContext(task.getJobId(),"evaluation-new",context.getBean(PlatformJobService.class));
        try(var attempt=service.start(task,next,Map.of())) {
            assertEquals("INTERRUPTED",jdbc.queryForObject("SELECT status FROM evaluation_execution_attempt WHERE id=?",String.class,old));
            attempt.inputs(List.of("q1")); attempt.record(evaluationQa(task,"new")); attempt.complete();
        }
        service.failTask(task,fixture.worker());
        assertEquals(2,jdbc.queryForObject("SELECT status FROM evaluation_task WHERE id=?",Integer.class,task.getId()));
    }
    @Test void cancelledJobsAreReconciledWithoutInventingCompletedResults() {
        var fixture=evaluationFixture("evaluation-cancel"); var service=context.getBean(EvaluationExecutionService.class);
        try(var attempt=service.start(fixture.task(),fixture.worker(),Map.of())) {
            attempt.inputs(List.of("q1"));
            jdbc.update("UPDATE platform_job SET status='CANCELLED' WHERE id=?",fixture.task().getJobId());
        }
        service.reconcileTerminatedJobs(); service.reconcileTerminatedJobs();
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM evaluation_execution_attempt",String.class));
        assertEquals(4,jdbc.queryForObject("SELECT status FROM evaluation_task",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_execution_result",Integer.class));
    }
    @Test void baselineRequiresIdenticalInputsAndPublishedCorpusAndCompletionIsAtomic() {
        var service=context.getBean(EvaluationExecutionService.class);
        var baseline=evaluationFixture("baseline-owner");
        try(var attempt=service.start(baseline.task(),baseline.worker(),Map.of())) {
            attempt.inputs(List.of("q1")); attempt.record(evaluationQa(baseline.task(),"q1")); attempt.complete();
        }
        var candidate=evaluationFixture("candidate-owner");
        try(var attempt=service.start(candidate.task(),candidate.worker(),Map.of())) {
            attempt.inputs(List.of("q1")); attempt.record(evaluationQa(candidate.task(),"q1"));
            assertTrue(service.comparable(candidate.task(),baseline.task()));
            jdbc.update("UPDATE evaluation_execution_attempt SET input_sha256=? WHERE id=?","f".repeat(64),candidate.task().getCurrentExecutionId());
            assertFalse(service.comparable(candidate.task(),baseline.task()));
            jdbc.update("UPDATE evaluation_execution_attempt SET input_sha256=(SELECT h FROM (SELECT input_sha256 h FROM evaluation_execution_attempt WHERE id=?) x) WHERE id=?",baseline.task().getCurrentExecutionId(),candidate.task().getCurrentExecutionId());
            jdbc.update("UPDATE evaluation_execution_attempt SET publication_sha256=? WHERE id=?","e".repeat(64),candidate.task().getCurrentExecutionId());
            assertFalse(service.comparable(candidate.task(),baseline.task()));
            jdbc.execute("CREATE TRIGGER it_reject_evaluation_complete BEFORE UPDATE ON evaluation_task FOR EACH ROW BEGIN IF NEW.status=2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected completion failure'; END IF; END");
            try { assertThrows(org.springframework.dao.DataAccessException.class,attempt::complete); }
            finally { jdbc.execute("DROP TRIGGER it_reject_evaluation_complete"); }
            assertEquals("RUNNING",jdbc.queryForObject("SELECT status FROM evaluation_execution_attempt WHERE id=?",String.class,candidate.task().getCurrentExecutionId()),"The archive cannot commit success when the task transaction rolls back");
        }
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM evaluation_execution_attempt WHERE id=?",String.class,candidate.task().getCurrentExecutionId()));
    }
    @Test void realEvaluationUsesOneKnowledgeConfigurationAndRejectsPartialSuccess() {
        var fixture=evaluationFixture("evaluation-full"); var task=fixture.task();
        jdbc.update("UPDATE knowledge_base SET vector_top_k=3 WHERE id=900001");
        var rag=org.mockito.Mockito.mock(RagSearchService.class);
        var seen=new ArrayList<Integer>(); var versions=new ArrayList<String>();
        var publications=context.getBean(PublishedIndexService.class);
        org.mockito.Mockito.when(rag.search(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(KnowledgeBase.class),org.mockito.ArgumentMatchers.isNull())).thenAnswer(call -> {
            KnowledgeBase frozen=call.getArgument(1); seen.add(frozen.getVectorTopK()); versions.add(publications.current(900001L).snapshot().version());
            if(seen.size()==1) {
                jdbc.update("UPDATE knowledge_base SET vector_top_k=99 WHERE id=900001");
                jdbc.update("INSERT INTO knowledge_document_relation(id,knowledge_id,document_id,index_revision,published_generation) VALUES(900001,900001,900001,1,'evaluation-new-head')");
                jdbc.update("INSERT INTO knowledge_index_generation(generation_key,relation_id,knowledge_id,document_id,index_revision,state) VALUES('evaluation-new-head',900001,900001,900001,1,'PUBLISHED')");
            } else throw new IllegalStateException("injected second-question failure");
            frozen.setVectorTopK(777); // Callers cannot mutate the owned snapshot for the next question.
            var result=new RagSearchService.RagSearchResult(); result.setDocuments(List.of()); return result;
        });
        var runtime=new cn.sdh.backend.service.impl.EvaluationServiceImpl(context.getBean(EvaluationTaskMapper.class),context.getBean(EvaluationQaMapper.class),context.getBean(VectorStoreService.class),org.mockito.Mockito.mock(KnowledgeBaseService.class),rag,org.mockito.Mockito.mock(ChatService.class),org.mockito.Mockito.mock(TestDatasetService.class),context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class),context.getBean(KnowledgeAccessService.class),context.getBean(PlatformJobService.class),context.getBean(EvaluationExecutionService.class));
        var q1=new cn.sdh.backend.dto.EvaluationQaItem(); q1.setQuestion("q1"); q1.setExpectedAnswer("answer");
        var q2=new cn.sdh.backend.dto.EvaluationQaItem(); q2.setQuestion("q2"); q2.setExpectedAnswer("answer");
        assertThrows(cn.sdh.backend.common.exception.EvaluationIntegrityException.class,() -> runtime.runImportedEvaluation(task.getId(),List.of(q1,q2),fixture.worker()));
        assertEquals(List.of(3,3),seen); assertEquals(versions.getFirst(),versions.getLast());
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM evaluation_execution_attempt",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT result_count FROM evaluation_execution_attempt",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT status FROM evaluation_task",Integer.class));
        try(var latest=publications.open(900001L)) { assertNotEquals(versions.getFirst(),latest.snapshot().version()); }
        jdbc.update("UPDATE knowledge_base SET vector_top_k=NULL WHERE id=900001");
    }

    @Test void indexPublicationFencesAnExpiredWorkerAndPreservesPinnedReads() {
        jdbc.update("INSERT IGNORE INTO knowledge_base(id,name,user_id) VALUES(900001,'index-it',1)");
        jdbc.update("INSERT IGNORE INTO knowledge_document(id,title,user_id) VALUES(900001,'index-it',1)");
        var jobs = context.getBean(PlatformJobService.class);
        var job = jobs.enqueue(new PlatformJobService.EnqueueCommand("index-fence", "DOCUMENT_INDEX", "DOCUMENT", 900001L, "{}", 1, 3, 1L));
        assertEquals(1,jobs.claimAvailable("index-owner-a",100).size(),"An immediate index job must be claimable after enqueue");
        jdbc.update("INSERT INTO knowledge_document_relation(id,knowledge_id,document_id,index_revision,job_id,process_status) VALUES(900001,900001,900001,1,?,1)",job.getId());
        var publications = context.getBean(PublishedIndexService.class);
        var payload = new cn.sdh.backend.job.DocumentIndexJobPayload(900001L,900001L,900001L,1,new cn.sdh.backend.dto.DocumentLinkConfig());
        var firstContext = new cn.sdh.backend.job.PlatformJobExecutionContext(job.getId(),"index-owner-a",jobs);
        var first = publications.stage(payload,job.getId(),"a".repeat(64),firstContext);
        try (var empty = publications.open(900001L)) {
            assertEquals(0,empty.snapshot().generationCount());
            jdbc.update("UPDATE platform_job SET lease_until=TIMESTAMPADD(SECOND,-1,NOW()) WHERE id=?",job.getId());
            assertEquals(1,jobs.claimAvailable("index-owner-b",100).size());
            assertThrows(IllegalStateException.class,() -> publications.publish(payload,job.getId(),first.key(),1,firstContext));
            assertNull(jdbc.queryForObject("SELECT published_generation FROM knowledge_document_relation WHERE id=900001",String.class));
            var nextContext = new cn.sdh.backend.job.PlatformJobExecutionContext(job.getId(),"index-owner-b",jobs);
            var next = publications.stage(payload,job.getId(),"a".repeat(64),nextContext);
            assertNotEquals(first.key(),next.key());
            assertThrows(BusinessException.class,() -> publications.publish(payload,job.getId(),first.key(),1,nextContext));
            jdbc.execute("CREATE TRIGGER it_reject_index_head BEFORE UPDATE ON knowledge_document_relation FOR EACH ROW BEGIN IF NEW.published_generation IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected publication failure'; END IF; END");
            try {
                assertThrows(org.springframework.dao.DataAccessException.class,() -> publications.publish(payload,job.getId(),next.key(),1,nextContext));
                assertEquals("STAGING",jdbc.queryForObject("SELECT state FROM knowledge_index_generation WHERE generation_key=?",String.class,next.key()),"A failed pointer update must roll back generation publication");
                assertNull(jdbc.queryForObject("SELECT published_generation FROM knowledge_document_relation WHERE id=900001",String.class));
            } finally { jdbc.execute("DROP TRIGGER it_reject_index_head"); }
            publications.publish(payload,job.getId(),next.key(),1,nextContext);
            assertEquals(0,publications.current(900001L).snapshot().generationCount());
            assertEquals("STAGING",jdbc.queryForObject("SELECT state FROM knowledge_index_generation WHERE generation_key=?",String.class,first.key()));
        }
        try (var published = publications.open(900001L)) {
            assertEquals(1,published.snapshot().generationCount());
            assertEquals(0,published.snapshot().legacyCount());
        }
    }

    @Test void simultaneousWorkersHaveExactlyOneRunOwner() throws Exception {
        AgentRun run = createRun();
        var claimed = new CopyOnWriteArrayList<RunExecutionLeaseService.Lease>();
        try {
            var results = simultaneous(() -> {
                try { claimed.add(leases.claim(run.getRunKey(), 1L)); return 1; }
                catch (BusinessException conflict) { assertEquals(409, conflict.getCode()); return 0; }
            });
            assertEquals(1, results.stream().mapToInt(Integer::intValue).sum());
            assertEquals(1, runs.getByRunKey(run.getRunKey()).getExecutionEpoch());
        } finally { claimed.forEach(RunExecutionLeaseService.Lease::close); }
    }
    @Test void gateReceiptAndBusinessPublicationShareTheHeadSwitchTransaction() {
        jdbc.update("UPDATE knowledge_document SET status=0 WHERE id=900001");
        jdbc.update("INSERT INTO knowledge_draft(id,knowledge_id,title,content,status,regression_queries,published_document_id,content_checksum,created_by,reviewed_by,reviewed_at,created_at,updated_at) VALUES(900001,900001,'approved',?,'PUBLISHING','[\"question\"]',900001,'checksum',1,1,NOW(),NOW(),NOW())","approved content ".repeat(10));
        var jobs=context.getBean(PlatformJobService.class);
        var job=jobs.enqueue(new PlatformJobService.EnqueueCommand("gate-index","DOCUMENT_INDEX","DOCUMENT",900001L,"{}",1,3,1L));
        assertEquals(1,jobs.claimAvailable("gate-owner",100).size());
        jdbc.update("INSERT INTO knowledge_document_relation(id,knowledge_id,document_id,index_revision,job_id,process_status) VALUES(900001,900001,900001,1,?,1)",job.getId());
        var payload=new cn.sdh.backend.job.DocumentIndexJobPayload(900001L,900001L,900001L,1,new cn.sdh.backend.dto.DocumentLinkConfig());
        var worker=new cn.sdh.backend.job.PlatformJobExecutionContext(job.getId(),"gate-owner",jobs);
        var publications=context.getBean(PublishedIndexService.class);
        String raw=cn.sdh.backend.utils.ContentFingerprint.sha256(KnowledgePublicationContent.markdown("approved","approved content ".repeat(10)));
        var stage=publications.stage(payload,job.getId(),"b".repeat(64),raw,worker);
        assertThrows(BusinessException.class,() -> publications.publish(payload,job.getId(),stage.key(),1,worker));
        try(var view=publications.openCandidate(payload,job.getId(),stage.key(),worker)) {
            publications.saveGateEvaluation(payload,job.getId(),stage.key(),view.basisVersion(),view.snapshot().version(),new KnowledgePublishingService.ValidationResult(true,List.of(new KnowledgePublishingService.ValidationItem("question",true,1))),worker);
        }
        jdbc.execute("CREATE TRIGGER it_reject_business_publication BEFORE UPDATE ON knowledge_draft FOR EACH ROW BEGIN IF NEW.status='PUBLISHED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected business publication failure'; END IF; END");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class,() -> publications.publish(payload,job.getId(),stage.key(),1,worker));
            assertNull(jdbc.queryForObject("SELECT published_generation FROM knowledge_document_relation WHERE id=900001",String.class));
            assertEquals("STAGING",jdbc.queryForObject("SELECT state FROM knowledge_index_generation WHERE generation_key=?",String.class,stage.key()));
            assertEquals("PUBLISHING",jdbc.queryForObject("SELECT status FROM knowledge_draft WHERE id=900001",String.class));
        } finally { jdbc.execute("DROP TRIGGER it_reject_business_publication"); }
        assertTrue(publications.publish(payload,job.getId(),stage.key(),1,worker));
        assertEquals(stage.key(),jdbc.queryForObject("SELECT published_generation FROM knowledge_draft WHERE id=900001",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT status FROM knowledge_document WHERE id=900001",Integer.class));
        assertEquals("PASSED",jdbc.queryForObject("SELECT status FROM knowledge_index_publication_gate",String.class));
        jdbc.update("UPDATE knowledge_draft SET status='ROLLED_BACK' WHERE id=900001");
        assertEquals(0,context.getBean(KnowledgeDraftMapper.class).updateValidation(900001L,stage.key(),"FAILED","{}"));
        try(var revoked=publications.open(900001L)) { assertEquals(0,revoked.snapshot().generationCount(),"Revoked business admission must hide a lingering technical head"); }
    }

    @Test void cancelledIndexJobsDoNotLeaveDraftsPublishingForever() {
        jdbc.update("INSERT INTO knowledge_draft(id,knowledge_id,title,content,status,published_document_id,content_checksum,created_by,created_at,updated_at) VALUES(900001,900001,'cancelled','content','PUBLISHING',900001,'checksum',1,NOW(),NOW())");
        var job=context.getBean(PlatformJobService.class).enqueue(new PlatformJobService.EnqueueCommand("cancel-index","DOCUMENT_INDEX","DOCUMENT",900001L,"{}",1,1,1L));
        jdbc.update("INSERT INTO knowledge_document_relation(id,knowledge_id,document_id,index_revision,job_id,process_status) VALUES(900001,900001,900001,1,?,0)",job.getId());
        jdbc.update("UPDATE platform_job SET status='CANCELLED' WHERE id=?",job.getId());
        context.getBean(IndexPublicationGateStore.class).reconcileTerminatedJobs();
        assertEquals("PUBLISH_FAILED",jdbc.queryForObject("SELECT status FROM knowledge_draft WHERE id=900001",String.class));
        assertNull(jdbc.queryForObject("SELECT published_generation FROM knowledge_document_relation WHERE id=900001",String.class));
    }
    @Test void competingPublishRequestsCreateOneJobAndDispatchFailureRollsBackEverything() throws Exception {
        jdbc.update("INSERT INTO knowledge_draft(id,knowledge_id,title,content,status,regression_queries,content_checksum,created_by,reviewed_by,reviewed_at,created_at,updated_at) VALUES(900001,900001,'atomic-publish',?,'APPROVED','[\"question\"]','checksum',1,1,NOW(),NOW(),NOW())","approved content ".repeat(10));
        var jobs=context.getBean(PlatformJobService.class);
        var processor=context.getBean(DocumentProcessService.class);
        var minio=context.getBean(MinioService.class);
        var publishing=context.getBean(KnowledgePublishingService.class);
        var actor=new UserContext(); actor.setUserId(1L); UserContext.setUserContext(actor);
        org.mockito.Mockito.doAnswer(call -> {
            Long document=call.getArgument(0);
            jobs.enqueue(new PlatformJobService.EnqueueCommand("draft-index:"+document,"DOCUMENT_INDEX","DOCUMENT",document,"{}",1,3,1L));
            throw new IllegalStateException("injected dispatch failure");
        }).when(processor).processDocumentForKnowledge(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.any(cn.sdh.backend.dto.DocumentLinkConfig.class));
        assertThrows(IllegalStateException.class,() -> publishing.publish(900001L));
        assertEquals(0,count("platform_job")); assertEquals(0,count("knowledge_document_relation")); assertEquals(0,count("knowledge_publish_record"));
        assertEquals("APPROVED",jdbc.queryForObject("SELECT status FROM knowledge_draft WHERE id=900001",String.class));
        org.mockito.Mockito.verify(minio).deleteFile(org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.doAnswer(call -> {
            Long document=call.getArgument(0);
            jobs.enqueue(new PlatformJobService.EnqueueCommand("draft-index:"+document,"DOCUMENT_INDEX","DOCUMENT",document,"{}",1,3,1L)); return null;
        }).when(processor).processDocumentForKnowledge(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.any(cn.sdh.backend.dto.DocumentLinkConfig.class));
        var results=simultaneous(() -> {
            var user=new UserContext(); user.setUserId(1L); UserContext.setUserContext(user);
            try { return publishing.publish(900001L).getStatus(); }
            catch(BusinessException conflict) { assertEquals(409,conflict.getCode()); return "CONFLICT"; }
            finally { UserContext.clear(); }
        });
        assertEquals(Set.of("PUBLISHING","CONFLICT"),new HashSet<>(results));
        assertEquals(1,count("platform_job")); assertEquals(1,count("knowledge_document_relation")); assertEquals(1,count("knowledge_publish_record"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_document WHERE title='atomic-publish'",Integer.class));
    }

    @Test void indexReadsUseFreshLeasesOutsideAnOlderRepeatableReadSnapshot() {
        jdbc.update("INSERT IGNORE INTO knowledge_base(id,name,user_id) VALUES(900001,'index-it',1)");
        jdbc.update("INSERT IGNORE INTO knowledge_document(id,title,user_id) VALUES(900001,'index-it',1)");
        jdbc.update("INSERT INTO knowledge_document_relation(id,knowledge_id,document_id,index_revision,process_status,published_generation) VALUES(900001,900001,900001,1,2,'published-old')");
        jdbc.update("INSERT INTO knowledge_index_generation(generation_key,relation_id,knowledge_id,document_id,index_revision,state) VALUES('published-old',900001,900001,900001,1,'PUBLISHED')");
        var publications=context.getBean(PublishedIndexService.class);
        var lifecycle=context.getBean(IndexGenerationLifecycle.class);
        context.getBean(cn.sdh.backend.config.IndexLifecycleProperties.class).setRetentionSeconds(0);
        var transactions=new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        transactions.executeWithoutResult(status -> {
            assertEquals("published-old",jdbc.queryForObject("SELECT published_generation FROM knowledge_document_relation WHERE id=900001",String.class));
            assertDoesNotThrow(() -> CompletableFuture.runAsync(() -> {
                var separate=new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
                separate.executeWithoutResult(s -> {
                    jdbc.update("INSERT INTO knowledge_index_generation(generation_key,relation_id,knowledge_id,document_id,index_revision,state) VALUES('published-new',900001,900001,900001,2,'PUBLISHED')");
                    jdbc.update("UPDATE knowledge_document_relation SET index_revision=2,published_generation='published-new' WHERE id=900001");
                });
            }).get(10,TimeUnit.SECONDS));
            try(var view=publications.open(900001L)) {
                assertTrue(view.filter("document_id").toString().contains("published-new"));
                assertEquals(1,view.snapshot().generationCount());
                var retired=lifecycle.claim(900001L);
                assertEquals(List.of("published-old"),retired.stream().map(c -> c.generation().key()).toList());
                assertTrue(lifecycle.finish(retired.getFirst(),true));
            }
            status.setRollbackOnly();
        });
        assertEquals(0,count("knowledge_index_read_lease"),"Closing a lease must commit independently of the caller rollback");
        assertEquals(1,count("knowledge_index_read_snapshot_generation"),"The immutable generation references remain available for the next reader");
    }

    @Test void competingCleanupWorkersRespectReadersAndRecoverAfterExpiry() throws Exception {
        jdbc.update("INSERT IGNORE INTO knowledge_base(id,name,user_id) VALUES(900001,'index-it',1)");
        jdbc.update("INSERT IGNORE INTO knowledge_document(id,title,user_id) VALUES(900001,'index-it',1)");
        jdbc.update("INSERT INTO knowledge_document_relation(id,knowledge_id,document_id,index_revision,process_status,published_generation) VALUES(900001,900001,900001,1,2,'orphan')");
        jdbc.update("INSERT INTO knowledge_index_generation(generation_key,relation_id,knowledge_id,document_id,index_revision,state) VALUES('orphan',900001,900001,900001,1,'PUBLISHED')");
        var lifecycle=context.getBean(IndexGenerationLifecycle.class);
        context.getBean(cn.sdh.backend.config.IndexLifecycleProperties.class).setRetentionSeconds(0);
        try(var reader=context.getBean(PublishedIndexService.class).open(900001L)) {
            jdbc.update("UPDATE knowledge_document_relation SET published_generation=NULL WHERE id=900001");
            assertTrue(lifecycle.claim(900001L).isEmpty());
            assertTrue(reader.filter("document_id").toString().contains("orphan"));
        }
        assertEquals(1,simultaneous(() -> lifecycle.claim(900001L).size()).stream().mapToInt(Integer::intValue).sum());
        assertEquals(1,jdbc.queryForObject("SELECT cleanup_attempts FROM knowledge_index_generation WHERE generation_key='orphan'",Integer.class));
        jdbc.update("UPDATE knowledge_index_generation SET cleanup_until=TIMESTAMPADD(SECOND,-1,NOW()) WHERE generation_key='orphan'");
        assertEquals(1,lifecycle.claim(900001L).size());
        lifecycle.reapExpiredLeases();
    }

    @Test void duplicateBusinessCommandsCommitOneActionAndOneJob() throws Exception {
        AgentTool tool = createTool("http://127.0.0.1:1/unused");
        var command = new AgentActionService.ActionCommand(tool.getId(), Map.of("order", "42"), null,
                "same-business-operation", null, null, null, null, null);
        var ids = simultaneous(() -> actions.request(command, 1L).getId());
        assertEquals(ids.getFirst(), ids.getLast());
        assertEquals(1, count("agent_action")); assertEquals(1, count("platform_job"));
    }

    @Test void idempotentJobLookupUsesTheCommittedWinnerOutsideItsOldSnapshot() {
        var jobs = context.getBean(PlatformJobService.class);
        var transactions = new org.springframework.transaction.support.TransactionTemplate(
                context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        var command = new PlatformJobService.EnqueueCommand("snapshot-race", "AGENT_ACTION_EXECUTE", "AGENT_ACTION", 42L, "{}", 1, 1, 1L);
        transactions.executeWithoutResult(status -> {
            assertEquals(0, count("platform_job")); // Establish a REPEATABLE READ snapshot before the other connection commits.
            var winner = assertDoesNotThrow(() -> CompletableFuture.supplyAsync(() -> jobs.enqueue(command)).get(10, TimeUnit.SECONDS));
            var duplicate = jobs.enqueue(command);
            assertNotNull(duplicate, "A committed winner must not disappear behind the losing transaction's old snapshot");
            assertEquals(winner.getId(), duplicate.getId());
        });
        assertEquals(1, count("platform_job"));
    }

    @Test void staleWorkerCannotOverwriteAnswerOrSdkCheckpoint() {
        AgentRun run = createRun();
        var store = context.getBean(JdbcAgentStateStore.class);
        var state = AgentState.builder().userId("1").sessionId(run.getRunKey()).build();
        var attempts = context.getBean(AgentExecutionAttemptService.class);
        try (var old = leases.claim(run.getRunKey(), 1L)) {
            var oldPrepared = prepared(run, old, "model-a");
            attempts.capture(oldPrepared, run);
            var scoped = store.fenced(old.fence());
            scoped.saveIfVersion("1", run.getRunKey(), "agent_state", state, 0);
            expire(run.getId()); assertEquals(1, leases.interruptExpired());
            var recovery = context.getBean(AgentRunRecoveryService.class);
            var receipt = recovery.request(run.getRunKey(), 1L);
            var jobs = context.getBean(PlatformJobService.class);
            jdbc.update("UPDATE platform_job SET status='RUNNING',lease_owner='worker-new',lease_until=TIMESTAMPADD(SECOND,120,NOW()) WHERE id=?", receipt.jobId());
            assertTrue(recovery.begin(new AgentRunRecoveryService.Payload(run.getRunKey(), receipt.generation()), jobs.get(receipt.jobId())));
            try (var next = leases.claim(run.getRunKey(), 1L)) {
                var current = attempts.capture(prepared(run, next, "model-b"), run);
                assertEquals(List.of("model"), com.alibaba.fastjson2.JSON.parseObject(current.snapshotJson()).getList("changedSections", String.class));
                assertThrows(BusinessException.class, () -> attempts.capture(oldPrepared, run));
                assertEquals(2, attempts.list(run.getId(), 1L).size());
                assertEquals(old.fence().epoch() + 1, next.fence().epoch());
                assertFalse(leases.renew(old.fence()));
                assertThrows(BusinessException.class, () -> scoped.saveIfVersion("1", run.getRunKey(), "agent_state", state, 0));
                var lifecycle = context.getBean(AgentRunLifecycle.class);
                var progress = new AgentRunLifecycle.Progress(1, 4, 2, 6);
                assertEquals("COMPLETED", lifecycle.complete(new AgentRunLifecycle.Context(next.fence(), null, null), progress, "new answer").getStatus());
                assertThrows(BusinessException.class, () -> lifecycle.complete(new AgentRunLifecycle.Context(old.fence(), null, null), progress, "old overwrite"));
                assertEquals("new answer", answer(run));
            }
        }
    }

    @Test void killingARealWorkerLeavesDurableOwnershipUntilExpiryAndThenInterrupts() throws Exception {
        AgentRun run = createRun();
        Process child = worker("lease", run, null);
        awaitSignal(child, "LEASE_READY");
        kill(child);
        assertNotNull(runs.getByRunKey(run.getRunKey()).getLeaseOwner());
        assertEquals(0, leases.interruptExpired());
        expire(run.getId()); assertEquals(1, leases.interruptExpired());
        assertEquals("INTERRUPTED", runs.getByRunKey(run.getRunKey()).getStatus());
        assertEquals(run.getRunKey(), jdbc.queryForObject("SELECT agent_run_key FROM chat_history WHERE id=?", String.class, run.getHistoryId()));
    }

    @Test void lifecyclePublicationRollsBackStateAndLeaseIfHistoryCannotBeWritten() {
        AgentRun run = createRun();
        try (var lease = leases.claim(run.getRunKey(), 1L)) {
            jdbc.update("DELETE FROM chat_history WHERE id=?", run.getHistoryId());
            var lifecycle = context.getBean(AgentRunLifecycle.class);
            assertThrows(IllegalStateException.class, () -> lifecycle.complete(new AgentRunLifecycle.Context(lease.fence(), null, null),
                    new AgentRunLifecycle.Progress(1, 4, 2, 6), "must not commit"));
            AgentRun stored = runs.getByRunKey(run.getRunKey());
            assertEquals("RUNNING", stored.getStatus());
            assertEquals(lease.fence().owner(), stored.getLeaseOwner());
            assertEquals(0, stored.getStepCount());
        }
    }

    @Test void aDatabaseCrashRollsBackAnUncommittedGuardedWrite() throws Exception {
        AgentRun run = createRun();
        Process child = worker("transaction", run, null);
        awaitSignal(child, "TRANSACTION_READY");
        mysql.crash(); kill(child);
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.queryForObject("SELECT 1", Integer.class));
        mysql.start();
        assertEquals("original", answer(run));
        assertEquals("RUNNING", runs.getByRunKey(run.getRunKey()).getStatus());
        expire(run.getId()); assertEquals(1, leases.interruptExpired());
    }

    @Test void downstreamSuccessThenWorkerDeathBecomesUnknownWithoutSendingASecondWrite() throws Exception {
        var received = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        // The production gateway deliberately forbids loopback, even when private-network tools are allowed.
        // Bind a local private interface so the real production HTTP validation and transport remain exercised.
        var address = java.net.NetworkInterface.networkInterfaces().flatMap(java.net.NetworkInterface::inetAddresses)
                .filter(ip -> ip instanceof java.net.Inet4Address && ip.isSiteLocalAddress() && !ip.isLoopbackAddress())
                .findFirst().orElseThrow(() -> new IllegalStateException("HTTP fault acceptance requires a local private IPv4 interface"));
        var server = HttpServer.create(new InetSocketAddress(address, 0), 0);
        String path = "/tickets/" + UUID.randomUUID();
        var httpThreads = Executors.newCachedThreadPool(task -> { var t = new Thread(task, "mysql-it-business"); t.setDaemon(true); return t; });
        server.setExecutor(httpThreads);
        server.createContext(path, exchange -> {
            try {
                jdbc.update("INSERT INTO it_business_receipt(payload) VALUES(?)", new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                received.countDown(); release.await(40, TimeUnit.SECONDS);
                byte[] body = "{\"ticket\":\"T-42\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
            } catch (Exception ignored) { /* A forcibly terminated client cannot read the response. */ }
            finally { exchange.close(); }
        });
        server.start();
        try {
            AgentTool tool = createTool("http://" + address.getHostAddress() + ":" + server.getAddress().getPort() + path);
            AgentAction action = actions.request(new AgentActionService.ActionCommand(tool.getId(), Map.of("order", "42"), null,
                    "crash-after-business-commit", null, null, null, null, null), 1L);
            Process child = worker("action", null, action.getId());
            assertTrue(received.await(30, TimeUnit.SECONDS), "The real HTTP write must reach the downstream before killing its worker");
            kill(child); release.countDown();
            var mapper = context.getBean(AgentActionMapper.class);
            assertEquals("EXECUTING", mapper.selectById(action.getId()).getStatus());
            jdbc.update("UPDATE agent_action SET update_time=TIMESTAMPADD(MINUTE,-10,NOW()) WHERE id=?", action.getId());
            assertEquals(1, mapper.recoverInterrupted(LocalDateTime.now().minusMinutes(5)));
            assertEquals("UNKNOWN", context.getBean(AgentActionExecutor.class).execute(action.getId(), 1).getStatus());
            UserContext actor = new UserContext(); actor.setUserId(1L); UserContext.setUserContext(actor);
            assertThrows(BusinessException.class, () -> actions.retry(action.getId()));
            assertEquals(1, count("it_business_receipt"));
        } finally { release.countDown(); server.stop(0); httpThreads.shutdownNow(); }
    }

    @Test void evaluationArchivesHaveOneWinnerAndSurviveLaterSourceChanges() throws Exception {
        var evaluation = context.getBean(cn.sdh.backend.agent.evaluation.AgentEvaluationService.class);
        var scenario = new cn.sdh.backend.agent.evaluation.AgentEvaluationData.Scenario("ticket", "创建工单", null,
                List.of("ticket"), List.of(), "返回编号", List.of(), cn.sdh.backend.agent.evaluation.AgentEvaluationData.Risk.MEDIUM);
        var dataset = evaluation.importDataset(new cn.sdh.backend.agent.evaluation.AgentEvaluationData.ImportRequest("验收", null, List.of(scenario)), 1L);
        AgentRun run = createRun();
        try (var lease = leases.claim(run.getRunKey(), 1L)) {
            context.getBean(AgentExecutionAttemptService.class).capture(prepared(run, lease, "model-at-execution"), run);
            context.getBean(AgentRunLifecycle.class).complete(new AgentRunLifecycle.Context(lease.fence(), null, null),
                    new AgentRunLifecycle.Progress(1, 4, 2, 6), "ticket-42");
        }
        var command = new cn.sdh.backend.agent.evaluation.AgentEvaluationData.RecordRequest(dataset.id(), "ticket", run.getRunKey());
        var ids = simultaneous(() -> evaluation.record(command, 1L).id());
        assertEquals(ids.getFirst(), ids.getLast()); assertEquals(1, count("agent_evaluation_record"));
        String snapshot = evaluation.detail(ids.getFirst(), 1L).runSnapshotJson();
        assertEquals("ticket-42", com.alibaba.fastjson2.JSON.parseObject(snapshot).getString("answer"));
        assertEquals("COMPLETE", com.alibaba.fastjson2.JSON.parseObject(snapshot).getString("configurationCoverage"));
        assertEquals(1, com.alibaba.fastjson2.JSON.parseObject(snapshot).getJSONArray("executionAttempts").size());
        jdbc.update("UPDATE chat_history SET answer='changed' WHERE id=?", run.getHistoryId());
        assertEquals(snapshot, evaluation.detail(ids.getFirst(), 1L).runSnapshotJson());
        assertEquals(404, assertThrows(BusinessException.class, () -> evaluation.detail(ids.getFirst(), 2L)).getCode());
    }

    @Test void evaluationDuplicateLookupSeesWinnerOutsideItsOldSnapshot() throws Exception {
        var evaluation = context.getBean(cn.sdh.backend.agent.evaluation.AgentEvaluationService.class);
        var scenario = new cn.sdh.backend.agent.evaluation.AgentEvaluationData.Scenario("ticket", "创建工单", null,
                List.of(), List.of(), "返回编号", List.of(), cn.sdh.backend.agent.evaluation.AgentEvaluationData.Risk.LOW);
        var dataset = evaluation.importDataset(new cn.sdh.backend.agent.evaluation.AgentEvaluationData.ImportRequest("验收", null, List.of(scenario)), 1L);
        AgentRun run = createRun();
        try (var lease = leases.claim(run.getRunKey(), 1L)) {
            context.getBean(AgentRunLifecycle.class).complete(new AgentRunLifecycle.Context(lease.fence(), null, null),
                    new AgentRunLifecycle.Progress(1, 4, 2, 6), "ticket-42");
        }
        var command = new cn.sdh.backend.agent.evaluation.AgentEvaluationData.RecordRequest(dataset.id(), "ticket", run.getRunKey());
        var transactions = new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        transactions.executeWithoutResult(status -> {
            assertEquals(0, count("agent_evaluation_record"));
            var winner = assertDoesNotThrow(() -> CompletableFuture.supplyAsync(() -> evaluation.record(command, 1L)).get(10, TimeUnit.SECONDS));
            assertEquals(winner.id(), evaluation.record(command, 1L).id());
        });
        assertEquals(1, count("agent_evaluation_record"));
    }

    @Test void competingDatasetRevisionsCannotOverwriteOrForkTheirParent() throws Exception {
        var evaluation = context.getBean(cn.sdh.backend.agent.evaluation.AgentEvaluationService.class);
        var scenario = new cn.sdh.backend.agent.evaluation.AgentEvaluationData.Scenario("ticket", "创建工单", null,
                List.of(), List.of(), "返回编号", List.of(), cn.sdh.backend.agent.evaluation.AgentEvaluationData.Risk.LOW);
        var root = evaluation.importDataset(new cn.sdh.backend.agent.evaluation.AgentEvaluationData.ImportRequest("v1", null, List.of(scenario)), 1L);
        var command = new cn.sdh.backend.agent.evaluation.AgentEvaluationData.ImportRequest("v2", root.id(), List.of(scenario));
        var successes = simultaneous(() -> {
            try { assertEquals(2, evaluation.importDataset(command, 1L).version()); return 1; }
            catch (BusinessException conflict) { assertEquals(409, conflict.getCode()); return 0; }
        });
        assertEquals(1, successes.stream().mapToInt(Integer::intValue).sum());
        assertEquals(2, count("agent_evaluation_dataset")); assertEquals(1, evaluation.dataset(root.id(), 1L).version());
    }

    @Test void attemptSnapshotsRollBackWithTheirOwningTransaction() {
        AgentRun run = createRun();
        var attempts = context.getBean(AgentExecutionAttemptService.class);
        var transactions = new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        try (var lease = leases.claim(run.getRunKey(), 1L)) {
            assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
                attempts.capture(prepared(run, lease, "model"), run);
                throw new IllegalStateException("force rollback");
            }));
            assertEquals(0, count("agent_execution_attempt"));
            assertNotNull(attempts.capture(prepared(run, lease, "model"), run));
        }
    }

    @Test void repeatedAttemptCaptureFindsItsWinnerOutsideAnOldSnapshot() {
        AgentRun run = createRun();
        var attempts = context.getBean(AgentExecutionAttemptService.class);
        var transactions = new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        try (var lease = leases.claim(run.getRunKey(), 1L)) {
            var prepared = prepared(run, lease, "model");
            transactions.executeWithoutResult(status -> {
                assertEquals(0, count("agent_execution_attempt"));
                var winner = assertDoesNotThrow(() -> CompletableFuture.supplyAsync(() -> attempts.capture(prepared, run)).get(10, TimeUnit.SECONDS));
                assertEquals(winner, attempts.capture(prepared, run));
            });
            assertEquals(1, count("agent_execution_attempt"));
        }
    }

    private AgentAskService.Prepared prepared(AgentRun run, RunExecutionLeaseService.Lease lease, String modelId) {
        ModelConfig config = new ModelConfig(); config.setId(run.getModelId()); config.setProvider("openai"); config.setModelId(modelId);
        return new AgentAskService.Prepared(run.getRunKey(), run.getId(), run.getHistoryId(), run.getSessionId(), run.getQuestion(),
                1L, null, new AgentExecutionPreparation.ModelBinding(config, cn.sdh.backend.service.factory.ModelProvider.OPENAI, null),
                "system", false, null, null, null, null, null, List.of(), 8, lease.fence().epoch() > 1, null, lease);
    }

    @Test void retrievalEvidenceIsInsertedOnceAndSurvivesAttemptAndArchivePublication() {
        String evidence = "{\"scope\":\"RETRIEVED_CANDIDATES\",\"generationCoverage\":\"COMPLETE\",\"chunks\":[{\"documentId\":42,\"indexRevision\":3,\"content\":\"source text\"}]}";
        AgentRun run = runs.create(1L, "evidence-session", "question", null, null, 8, evidence);
        run.setEngine("agentscope"); run.setEngineVersion("2.0.3"); runs.save(run);
        run.setRetrievalSnapshotJson("{\"replacement\":true}"); runs.save(run);
        assertEquals(evidence, runs.getByRunKey(run.getRunKey()).getRetrievalSnapshotJson());
        try (var lease = leases.claim(run.getRunKey(), 1L)) {
            var saved = runs.getByRunKey(run.getRunKey());
            var attempt = context.getBean(AgentExecutionAttemptService.class).capture(prepared(saved, lease, "model"), saved);
            assertEquals(cn.sdh.backend.utils.ContentFingerprint.sha256(evidence), com.alibaba.fastjson2.JSON.parseObject(attempt.snapshotJson())
                    .getJSONObject("configuration").getJSONObject("evidence").getString("retrievalSnapshotSha256"));
            context.getBean(AgentRunLifecycle.class).complete(new AgentRunLifecycle.Context(lease.fence(), null, null),
                    new AgentRunLifecycle.Progress(1, 4, 2, 6), "answer");
        }
        var evaluations = context.getBean(cn.sdh.backend.agent.evaluation.AgentEvaluationService.class);
        var scenario = new cn.sdh.backend.agent.evaluation.AgentEvaluationData.Scenario("query", "question", null, List.of(), List.of(), "answer", List.of(),
                cn.sdh.backend.agent.evaluation.AgentEvaluationData.Risk.LOW);
        var dataset = evaluations.importDataset(new cn.sdh.backend.agent.evaluation.AgentEvaluationData.ImportRequest("evidence", null, List.of(scenario)), 1L);
        var archive = evaluations.record(new cn.sdh.backend.agent.evaluation.AgentEvaluationData.RecordRequest(dataset.id(), "query", run.getRunKey()), 1L);
        assertEquals("source text", com.alibaba.fastjson2.JSON.parseObject(archive.runSnapshotJson()).getJSONObject("retrievalSnapshot")
                .getJSONArray("chunks").getJSONObject(0).getString("content"));
    }

    private AgentRun createRun() {
        ChatHistory history = new ChatHistory(); history.setUserId(1L); history.setSessionId(UUID.randomUUID().toString());
        history.setQuestion("mysql fault acceptance"); history.setAnswer("original"); history.setCreateTime(LocalDateTime.now());
        context.getBean(ChatHistoryMapper.class).insert(history);
        AgentRun run = runs.create(1L, history.getSessionId(), history.getQuestion(), null, null, 8);
        run.setHistoryId(history.getId()); run.setEngine("agentscope"); run.setEngineVersion("2.0.3"); runs.save(run); return run;
    }
    private AgentTool createTool(String endpoint) {
        AgentTool tool = new AgentTool(); tool.setName("MySQL fault test"); tool.setCode("it_" + UUID.randomUUID().toString().replace("-", ""));
        tool.setEndpoint(endpoint); tool.setHttpMethod("POST"); tool.setRiskLevel("LOW"); tool.setApprovalMode("NEVER");
        tool.setAuthType("none"); tool.setApproverId(1L); tool.setCreatedBy(1L); tool.setTimeoutSeconds(120);
        tool.setAllowPrivateNetwork(1); tool.setAllowInsecureHttp(1); tool.setStatus(1); tool.setIsSystem(0);
        tool.setCreateTime(LocalDateTime.now()); tool.setUpdateTime(LocalDateTime.now());
        context.getBean(AgentToolMapper.class).insert(tool); return tool;
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private void expire(Long id) { jdbc.update("UPDATE agent_run SET lease_until=TIMESTAMPADD(SECOND,-1,NOW()) WHERE id=?", id); }
    private String answer(AgentRun run) { return jdbc.queryForObject("SELECT answer FROM chat_history WHERE id=?", String.class, run.getHistoryId()); }
    private <T> List<T> simultaneous(Callable<T> action) throws Exception {
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<T> waiting = () -> { gate.await(); return action.call(); };
            var first = pool.submit(waiting); var second = pool.submit(waiting); gate.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }
    private Process worker(String mode, AgentRun run, Long actionId) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var builder = new ProcessBuilder(java, "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), AgentFaultWorker.class.getName());
        var env = builder.environment(); env.put("SDH_IT_CHILD_URL", mysql.url(schema)); env.put("SDH_IT_CHILD_PASSWORD", mysql.password());
        env.put("SDH_IT_CHILD_MODE", mode);
        if (run != null) { env.put("SDH_IT_CHILD_RUN", run.getRunKey()); env.put("SDH_IT_CHILD_HISTORY", run.getHistoryId().toString()); }
        if (actionId != null) env.put("SDH_IT_CHILD_ACTION", actionId.toString());
        builder.redirectError(mysql.evidence().resolve("worker-" + mode + "-" + children.size() + ".log").toFile());
        Process child = builder.start(); children.add(child); return child;
    }
    private void awaitSignal(Process child, String signal) throws Exception {
        try (var reader = Executors.newSingleThreadExecutor(task -> { var t = new Thread(task); t.setDaemon(true); return t; })) {
            var ready = reader.submit(() -> {
                try (var output = child.inputReader()) { for (String line; (line = output.readLine()) != null;) if (signal.equals(line)) return true; }
                return false;
            });
            try { assertTrue(ready.get(30, TimeUnit.SECONDS), "Worker did not reach " + signal); }
            catch (Exception failure) { kill(child); throw failure; }
        }
    }
    private static void kill(Process child) throws Exception {
        if (child.isAlive()) child.destroyForcibly();
        assertTrue(child.waitFor(10, TimeUnit.SECONDS), "Owned worker did not terminate");
    }
}
