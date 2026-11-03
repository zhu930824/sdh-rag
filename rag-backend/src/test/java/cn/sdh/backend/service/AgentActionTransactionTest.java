package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.agent.RunStateService;
import cn.sdh.backend.agent.AgentRunRecoveryService;
import cn.sdh.backend.agent.RunExecutionLeaseService;
import cn.sdh.backend.agent.AgentActionRecoveryScheduler;
import cn.sdh.backend.mapper.AgentActionMapper;
import cn.sdh.backend.mapper.AgentActionFeedbackMapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring transactions + MyBatis + SQL: no mocked commit/rollback assertions. */
@SpringJUnitConfig(AgentActionTransactionTest.Config.class)
class AgentActionTransactionTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource source;
    @Autowired AgentActionService service;
    @Autowired AgentActionMapper actions;
    @Autowired AgentToolService tools;
    @Autowired AgentActionCompletionService completion;
    @Autowired RunStateService runs;
    @Autowired AgentActionRecoveryScheduler recovery;
    @Autowired RunExecutionLeaseService leases;
    @Autowired AgentActionExecutor executor;
    @Autowired AgentRunRecoveryService runRecovery;
    @Autowired PlatformJobService jobs;

    @BeforeEach void setup() {
        jdbc.execute("DROP TABLE IF EXISTS agent_runtime_state");
        jdbc.execute("DROP TABLE IF EXISTS agent_action");
        jdbc.execute("DROP TABLE IF EXISTS platform_job");
        jdbc.execute("DROP TABLE IF EXISTS agent_run");
        jdbc.execute("DROP TABLE IF EXISTS chat_history");
        jdbc.execute("CREATE TABLE agent_run(id BIGINT PRIMARY KEY, run_key VARCHAR(64), status VARCHAR(30), updated_at TIMESTAMP, history_id BIGINT, completed_at TIMESTAMP, user_id BIGINT, error_message VARCHAR(2000), engine VARCHAR(32), messages_json LONGTEXT, pending_action_id BIGINT, step_count INT, max_steps INT, total_tokens INT, prompt_tokens INT, completion_tokens INT)");
        jdbc.execute("CREATE TABLE chat_history(id BIGINT PRIMARY KEY, answer LONGTEXT, sources LONGTEXT, router_info LONGTEXT, knowledge_id BIGINT, model_id BIGINT, agent_run_key VARCHAR(64), agent_trace LONGTEXT, update_time TIMESTAMP)");
        jdbc.execute("""
            CREATE TABLE agent_action (
              id BIGINT AUTO_INCREMENT PRIMARY KEY, tool_id BIGINT, tool_name VARCHAR(100),
              workflow_id BIGINT, node_id VARCHAR(100), requested_by BIGINT, approver_id BIGINT,
              idempotency_key VARCHAR(128), request_json LONGTEXT, evidence_json LONGTEXT,
              run_id BIGINT, step_id BIGINT, workflow_execution_id BIGINT, tool_call_id VARCHAR(128),
              tool_definition_hash VARCHAR(64), status VARCHAR(30), result_json LONGTEXT,
              response_code INT, error_message VARCHAR(2000), requested_at TIMESTAMP, decided_at TIMESTAMP,
              executed_at TIMESTAMP, update_time TIMESTAMP,
              UNIQUE(tool_id, requested_by, idempotency_key))
            """);
        jdbc.execute("""
            CREATE TABLE platform_job (
              id BIGINT AUTO_INCREMENT PRIMARY KEY, job_key VARCHAR(160) UNIQUE, job_type VARCHAR(60),
              resource_type VARCHAR(40), resource_id BIGINT, payload_json LONGTEXT, status VARCHAR(24),
              priority INT, attempts INT, max_attempts INT, available_at TIMESTAMP, lease_owner VARCHAR(120),
              lease_until TIMESTAMP, heartbeat_at TIMESTAMP, trace_id VARCHAR(64), created_by BIGINT,
              result_summary VARCHAR(1000), error_message VARCHAR(2000), started_at TIMESTAMP,
              finished_at TIMESTAMP, create_time TIMESTAMP, update_time TIMESTAMP)
            """);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V32__reliable_agent_action_jobs.sql")).execute(source);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V33__agent_run_execution_lease.sql")).execute(source);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V34__agent_run_recovery.sql")).execute(source);
        jdbc.execute("CREATE TABLE agent_runtime_state(user_id VARCHAR(64),session_id VARCHAR(100),state_key VARCHAR(100),state_version BIGINT,payload LONGTEXT,PRIMARY KEY(user_id,session_id,state_key))");
        reset(tools, executor);
        AgentTool tool = new AgentTool(); tool.setId(1L); tool.setCode("create_ticket"); tool.setName("创建工单");
        tool.setEndpoint("https://business.example/tickets"); tool.setHttpMethod("POST");
        tool.setRiskLevel("LOW"); tool.setApprovalMode("NEVER"); tool.setApproverId(9L);
        when(tools.requireUsable(1L, 7L)).thenReturn(tool);
        UserContext user = new UserContext(); user.setUserId(7L); UserContext.setUserContext(user);
    }
    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void acceptingWriteCommitsActionAndVersionedJobTogether() {
        AgentAction action = service.request(command(), 7L);
        assertEquals("READY", action.getStatus());
        assertNotNull(action.getExecutionJobId());
        assertEquals(1, count("agent_action")); assertEquals(1, count("platform_job"));
        assertEquals(action.getExecutionJobId(), actions.selectById(action.getId()).getExecutionJobId());
        assertEquals(action.getId(), service.request(command(), 7L).getId());
        assertEquals(1, count("platform_job"));
    }

    @Test void queueFailureRollsBackNewAction() {
        rejectJobs("AGENT_ACTION_EXECUTE");
        assertThrows(RuntimeException.class, () -> service.request(command(), 7L));
        assertEquals(0, count("agent_action")); assertEquals(0, count("platform_job"));
    }

    @Test void resultAndContinuationRollbackTogetherWhenNotificationCannotBePersisted() {
        AgentAction action = service.request(command(), 7L);
        assertEquals(1, actions.claimReady(action.getId(), 1));
        AgentAction result = actions.selectById(action.getId());
        result.setRunId(42L); result.setStatus("SUCCEEDED"); result.setResultJson("ticket-42");
        rejectJobs("AGENT_ACTION_CONTINUE");
        assertThrows(RuntimeException.class, () -> completion.complete(result));
        assertEquals("EXECUTING", actions.selectById(action.getId()).getStatus());
        assertEquals(1, count("platform_job"));
    }

    @Test void retryCreatesNewAttemptAndOldWorkerCannotOverwriteIt() {
        AgentAction first = service.request(command(), 7L);
        jdbc.update("UPDATE agent_action SET status='FAILED' WHERE id=?", first.getId());
        AgentAction retry = service.retry(first.getId());
        assertEquals(2, retry.getExecutionVersion());
        assertNotEquals(first.getExecutionJobId(), retry.getExecutionJobId());
        assertEquals(0, actions.claimReady(first.getId(), 1));
        first.setStatus("SUCCEEDED");
        assertEquals(0, actions.finishExecuting(first));
        assertEquals(2, count("platform_job"));
    }

    @Test void failedApprovalEnqueueRestoresPendingApproval() {
        tools.requireUsable(1L, 7L).setApprovalMode("ALWAYS");
        AgentAction pending = service.request(command(), 7L);
        assertEquals("PENDING_APPROVAL", pending.getStatus());
        assertEquals(0, count("platform_job"));
        UserContext approver = new UserContext(); approver.setUserId(9L); UserContext.setUserContext(approver);
        rejectJobs("AGENT_ACTION_EXECUTE");
        assertThrows(RuntimeException.class, () -> service.approve(pending.getId()));
        assertEquals("PENDING_APPROVAL", actions.selectById(pending.getId()).getStatus());
    }

    @Test void answerPublicationFailureCannotExposeACompletedRun() {
        jdbc.update("INSERT INTO agent_run(id,run_key,status) VALUES(1,'run-1','RUNNING')");
        AgentRun run = new AgentRun(); run.setId(1L); run.setRunKey("run-1");
        run.setHistoryId(999L); run.setStatus("COMPLETED");
        assertThrows(RuntimeException.class, () -> runs.publish(run, "RUNNING", "最终答案", null, null, "{}"));
        assertEquals("RUNNING", jdbc.queryForObject("SELECT status FROM agent_run WHERE id=1", String.class));
    }

    @Test void answerAndCompletionAreVisibleTogether() {
        jdbc.update("INSERT INTO agent_run(id,run_key,status) VALUES(1,'run-1','RUNNING')");
        jdbc.update("INSERT INTO chat_history(id) VALUES(8)");
        AgentRun run = new AgentRun(); run.setId(1L); run.setRunKey("run-1");
        run.setHistoryId(8L); run.setStatus("COMPLETED");
        assertTrue(runs.publish(run, "RUNNING", "最终答案", null, null, "{}"));
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM agent_run WHERE id=1", String.class));
        assertEquals("最终答案", jdbc.queryForObject("SELECT answer FROM chat_history WHERE id=8", String.class));
        assertFalse(runs.publish(run, "RUNNING", "重复覆盖", null, null, "{}"));
    }

    @Test void aCancelledUndispatchedJobBecomesAVisibleFailedActionWithoutSendingAnything() {
        AgentAction action = service.request(command(), 7L);
        jdbc.update("UPDATE platform_job SET status='CANCELLED' WHERE id=?", action.getExecutionJobId());
        assertEquals(1, actions.selectRecoveryCandidates().size());
        recovery.recover();
        assertEquals("FAILED", actions.selectById(action.getId()).getStatus());
    }

    @Test void unknownActionsRequireReconciliationBeforeRetry() {
        AgentAction action = service.request(command(), 7L);
        jdbc.update("UPDATE agent_action SET status='UNKNOWN' WHERE id=?", action.getId());
        assertThrows(RuntimeException.class, () -> service.retry(action.getId()));
        assertEquals("UNKNOWN", actions.selectById(action.getId()).getStatus());
        assertEquals(1, count("platform_job"));
    }

    @Test void resumingAnOlderKeyFormatReusesItsExistingOperation() {
        jdbc.update("INSERT INTO agent_run(id,run_key,user_id,status) VALUES(42,'run-42',7,'RUNNING')");
        try (var lease = leases.claim("run-42", 7L)) {
        AgentAction original = service.request(new AgentActionService.ActionCommand(1L, Map.of("orderId", "42"), null,
                "legacy-key", null, null, 42L, null, null, "call-1", lease.fence()), 7L);
        AgentAction resumed = service.request(new AgentActionService.ActionCommand(1L, Map.of("orderId", "42"), null,
                "new-key", null, null, 42L, null, null, "call-2", lease.fence()), 7L);
        assertEquals(original.getId(), resumed.getId());
        assertEquals(1, count("platform_job"));
        }
    }

    @Test void staleWorkerCannotRegisterANewBusinessAction() {
        jdbc.update("INSERT INTO agent_run(id,run_key,user_id,status) VALUES(42,'run-42',7,'RUNNING')");
        try (var lease = leases.claim("run-42", 7L)) {
            jdbc.update("UPDATE agent_run SET lease_until=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) WHERE id=42");
            assertThrows(RuntimeException.class, () -> service.request(new AgentActionService.ActionCommand(
                    1L, Map.of(), null, "late", null, null, 42L, null, null, "call", lease.fence()), 7L));
            assertEquals(0, count("agent_action"));
            assertEquals(0, count("platform_job"));
        }
    }

    @Test void finalPublicationUsesFenceAndReleasesLeaseAtomically() {
        jdbc.update("INSERT INTO agent_run(id,run_key,user_id,status,history_id) VALUES(42,'run-42',7,'RUNNING',8)");
        jdbc.update("INSERT INTO chat_history(id,answer) VALUES(8,'原回答')");
        try (var lease = leases.claim("run-42", 7L)) {
            AgentRun finalRun = new AgentRun(); finalRun.setId(42L); finalRun.setRunKey("run-42");
            finalRun.setHistoryId(8L); finalRun.setStatus("COMPLETED"); finalRun.setExecutionEpoch(999L);
            assertFalse(runs.publish(finalRun, "RUNNING", "未持有租约", null, null, "{}"));
            assertTrue(runs.publish(lease.fence(), finalRun, "RUNNING", "正确答案", null, null, "{}"));
            assertNull(jdbc.queryForObject("SELECT lease_owner FROM agent_run WHERE id=42", String.class));
            assertEquals(1, jdbc.queryForObject("SELECT execution_epoch FROM agent_run WHERE id=42", Long.class));
            assertThrows(RuntimeException.class, () -> runs.publish(lease.fence(), finalRun, "RUNNING", "旧任务覆盖", null, null, "{}"));
            assertEquals("正确答案", jdbc.queryForObject("SELECT answer FROM chat_history WHERE id=8", String.class));
        }
    }

    @Test void readToolDispatchHappensAfterRegistrationCommitsWithoutHoldingATransaction() {
        tools.requireUsable(1L, 7L).setHttpMethod("GET");
        when(executor.execute(anyLong(), eq(1))).thenAnswer(call -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(1, count("agent_action"));
            return actions.selectById(call.getArgument(0));
        });
        assertNotNull(service.request(command(), 7L));
        assertEquals(0, count("platform_job"));
    }

    @Test void ownerRecoveryQueuesExactlyOneJobPerGenerationAndPublishesState() {
        interruptedRun();
        var first = runRecovery.request("recover-42", 7L);
        var duplicate = runRecovery.request("recover-42", 7L);
        assertEquals(first.jobId(), duplicate.jobId());
        assertEquals(1, first.generation());
        assertEquals("RECOVERY_QUEUED", runStatus());
        assertEquals(1, count("platform_job"));
        assertTrue(jdbc.queryForObject("SELECT answer FROM chat_history WHERE id=8", String.class).contains("队列"));
        assertEquals("recover-42", jdbc.queryForObject("SELECT agent_run_key FROM chat_history WHERE id=8", String.class));
    }

    @Test void recoveryQueueFailureRollsBackStateAndGeneration() {
        interruptedRun(); rejectJobs("AGENT_RECOVER");
        assertThrows(RuntimeException.class, () -> runRecovery.request("recover-42", 7L));
        assertEquals("INTERRUPTED", runStatus());
        assertEquals(0, jdbc.queryForObject("SELECT recovery_generation FROM agent_run WHERE id=42", Long.class));
        assertEquals("中断前回答", jdbc.queryForObject("SELECT answer FROM chat_history WHERE id=8", String.class));
    }

    @Test void simultaneousRecoveryRequestsShareOneDurableJob() throws Exception {
        interruptedRun();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<AgentRunRecoveryService.Receipt> request = () -> {
                start.await(); return runRecovery.request("recover-42", 7L);
            };
            var first = workers.submit(request); var second = workers.submit(request); start.countDown();
            assertEquals(first.get(5, java.util.concurrent.TimeUnit.SECONDS).jobId(), second.get(5, java.util.concurrent.TimeUnit.SECONDS).jobId());
            assertEquals(1, count("platform_job"));
        }
    }

    @Test void anotherUserAndAnyExistingBusinessActionBlockRecovery() {
        interruptedRun();
        assertThrows(RuntimeException.class, () -> runRecovery.request("recover-42", 9L));
        var action = service.request(command(), 7L);
        jdbc.update("UPDATE agent_action SET run_id=42,status='SUCCEEDED' WHERE id=?", action.getId());
        assertFalse(runRecovery.diagnose("recover-42", 7L).canRecover());
        assertThrows(RuntimeException.class, () -> runRecovery.request("recover-42", 7L));
        assertEquals("INTERRUPTED", runStatus());
    }

    @Test void cancelledRecoveryReturnsControlToTheOwnerAndOldJobCannotStartNextGeneration() {
        interruptedRun();
        var first = runRecovery.request("recover-42", 7L);
        var oldJob = jobs.get(first.jobId());
        jdbc.update("UPDATE platform_job SET status='CANCELLED' WHERE id=?", first.jobId());
        runRecovery.reconcileQueue();
        assertEquals("INTERRUPTED", runStatus());
        var next = runRecovery.request("recover-42", 7L);
        assertEquals(2, next.generation());
        assertNotEquals(first.jobId(), next.jobId());
        assertFalse(runRecovery.begin(new AgentRunRecoveryService.Payload("recover-42", 1), oldJob));
        assertEquals("RECOVERY_QUEUED", runStatus());
    }

    @Test void recoveryRechecksFactsAndResetsOnlyTheOwnedCheckpointBeforeStartingOnce() {
        interruptedRun();
        jdbc.update("INSERT INTO agent_runtime_state VALUES('7','recover-42','agent_state',1,'original')");
        jdbc.update("INSERT INTO agent_runtime_state VALUES('8','recover-42','agent_state',1,'other user')");
        var receipt = runRecovery.request("recover-42", 7L);
        var job = jobs.get(receipt.jobId());
        jdbc.update("UPDATE platform_job SET status='RUNNING',lease_owner='worker-1',lease_until=DATEADD('MINUTE',5,CURRENT_TIMESTAMP) WHERE id=?", job.getId());
        job = jobs.get(job.getId());
        var payload = new AgentRunRecoveryService.Payload("recover-42", 1);
        assertTrue(runRecovery.begin(payload, job));
        assertEquals("RUNNING", runStatus());
        assertEquals(1, count("agent_runtime_state"));
        assertEquals("other user", jdbc.queryForObject("SELECT payload FROM agent_runtime_state", String.class));
        assertFalse(runRecovery.begin(payload, job));
        try (var lease = leases.claim("recover-42", 7L)) {
            assertEquals(2, lease.fence().epoch());
            runRecovery.failBeforeClaim(payload, job.getId());
            assertEquals("RUNNING", runStatus());
        }
    }

    @Test void resetFailureRollsBackCheckpointDeletionAndTheQueuedState() {
        interruptedRun();
        jdbc.update("INSERT INTO agent_runtime_state VALUES('7','recover-42','agent_state',1,'original')");
        var receipt = runRecovery.request("recover-42", 7L);
        jdbc.update("DELETE FROM chat_history WHERE id=8");
        jdbc.update("UPDATE platform_job SET status='RUNNING',lease_owner='worker-1',lease_until=DATEADD('MINUTE',5,CURRENT_TIMESTAMP) WHERE id=?", receipt.jobId());
        assertThrows(RuntimeException.class, () -> runRecovery.begin(new AgentRunRecoveryService.Payload("recover-42", 1), jobs.get(receipt.jobId())));
        assertEquals("RECOVERY_QUEUED", runStatus());
        assertEquals("original", jdbc.queryForObject("SELECT payload FROM agent_runtime_state", String.class));
    }

    @Test void newActionFactsBlockAQueuedRecoveryAtTheWorkerBoundary() {
        interruptedRun();
        var receipt = runRecovery.request("recover-42", 7L);
        var action = service.request(command(), 7L);
        jdbc.update("UPDATE agent_action SET run_id=42 WHERE id=?", action.getId());
        jdbc.update("UPDATE platform_job SET status='RUNNING',lease_owner='worker-1',lease_until=DATEADD('MINUTE',5,CURRENT_TIMESTAMP) WHERE id=?", receipt.jobId());
        assertThrows(RuntimeException.class, () -> runRecovery.begin(new AgentRunRecoveryService.Payload("recover-42", 1), jobs.get(receipt.jobId())));
        assertEquals("RECOVERY_QUEUED", runStatus());
    }

    @Test void preparationFailureMakesTheRunRecoverableAgainWithoutResettingGeneration() {
        interruptedRun();
        var receipt = runRecovery.request("recover-42", 7L);
        jdbc.update("UPDATE platform_job SET status='RUNNING',lease_owner='worker-1',lease_until=DATEADD('MINUTE',5,CURRENT_TIMESTAMP) WHERE id=?", receipt.jobId());
        var payload = new AgentRunRecoveryService.Payload("recover-42", 1);
        assertTrue(runRecovery.begin(payload, jobs.get(receipt.jobId())));
        runRecovery.failBeforeClaim(payload, receipt.jobId());
        assertEquals("INTERRUPTED", runStatus());
        assertEquals(2, runRecovery.request("recover-42", 7L).generation());
    }

    private void interruptedRun() {
        jdbc.update("INSERT INTO chat_history(id,answer) VALUES(8,'中断前回答')");
        jdbc.update("INSERT INTO agent_run(id,run_key,user_id,status,engine,execution_epoch,history_id) VALUES(42,'recover-42',7,'INTERRUPTED','agentscope',1,8)");
    }

    @Test void exhaustedBudgetCannotCreateAnotherRecoveryJob() {
        interruptedRun();
        jdbc.update("UPDATE agent_run SET total_tokens=200000 WHERE id=42");
        assertFalse(runRecovery.diagnose("recover-42", 7L).canRecover());
        assertThrows(RuntimeException.class, () -> runRecovery.request("recover-42", 7L));
        assertEquals(0, count("platform_job"));
        assertEquals("INTERRUPTED", runStatus());
    }

    @Test void aQueuedRecoveryMustRecheckTheBudgetBeforeDeletingItsCheckpoint() {
        interruptedRun();
        var receipt = runRecovery.request("recover-42", 7L);
        jdbc.update("INSERT INTO agent_runtime_state VALUES('7','recover-42','agent_state',1,'original')");
        jdbc.update("UPDATE agent_run SET step_count=8,max_steps=8 WHERE id=42");
        jdbc.update("UPDATE platform_job SET status='RUNNING',lease_owner='worker-1',lease_until=DATEADD('MINUTE',5,CURRENT_TIMESTAMP) WHERE id=?", receipt.jobId());
        assertThrows(RuntimeException.class, () -> runRecovery.begin(new AgentRunRecoveryService.Payload("recover-42", 1), jobs.get(receipt.jobId())));
        assertEquals("RECOVERY_QUEUED", runStatus());
        assertEquals("original", jdbc.queryForObject("SELECT payload FROM agent_runtime_state", String.class));
    }

    @Test void understatedTotalDoesNotHideActualUsageFromRecoveryPolicy() {
        interruptedRun();
        jdbc.update("UPDATE agent_run SET total_tokens=0,prompt_tokens=120000,completion_tokens=80000 WHERE id=42");
        assertFalse(runRecovery.diagnose("recover-42", 7L).canRecover());
        assertThrows(RuntimeException.class, () -> runRecovery.request("recover-42", 7L));
        assertEquals(0, count("platform_job"));
    }

    @Test void cancellationAfterActivationButBeforeClaimIsNotMisreportedAsAnInterruption() {
        interruptedRun();
        var receipt = runRecovery.request("recover-42", 7L);
        jdbc.update("UPDATE platform_job SET status='RUNNING',lease_owner='worker-1',lease_until=DATEADD('MINUTE',5,CURRENT_TIMESTAMP) WHERE id=?", receipt.jobId());
        var payload = new AgentRunRecoveryService.Payload("recover-42", 1);
        assertTrue(runRecovery.begin(payload, jobs.get(receipt.jobId())));
        jdbc.update("UPDATE platform_job SET status='CANCEL_REQUESTED' WHERE id=?", receipt.jobId());
        runRecovery.failBeforeClaim(payload, receipt.jobId());
        assertEquals("CANCELLED", runStatus());
        assertFalse(runRecovery.diagnose("recover-42", 7L).canRecover());
    }
    private String runStatus() { return jdbc.queryForObject("SELECT status FROM agent_run WHERE id=42", String.class); }

    private AgentActionService.ActionCommand command() {
        return new AgentActionService.ActionCommand(1L, Map.of("orderId", "42"), null,
                "request-1", null, null, null, null, null);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private void rejectJobs(String type) {
        jdbc.execute("ALTER TABLE platform_job ADD CONSTRAINT reject_jobs CHECK(job_type <> '" + type + "')");
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = AgentActionMapper.class)
    @Import({AgentActionService.class, AgentActionJobs.class, AgentActionCompletionService.class, PlatformJobService.class, RunStateService.class, AgentActionRecoveryScheduler.class, RunExecutionLeaseService.class, AgentRunRecoveryService.class})
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:agentTx;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); return factory.getObject();
        }
        @Bean AgentToolService tools() { return mock(AgentToolService.class); }
        @Bean AgentActionExecutor executor() { return mock(AgentActionExecutor.class); }
        @Bean AgentImprovementService improvements() { return mock(AgentImprovementService.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }
}
