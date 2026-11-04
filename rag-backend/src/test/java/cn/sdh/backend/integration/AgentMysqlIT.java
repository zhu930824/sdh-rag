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
        var upgraded = Flyway.configure().dataSource(source).target("35").load();
        assertEquals(5, upgraded.migrate().migrationsExecuted, "V30 -> V35 must apply exactly five migrations");
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
        for (String table : List.of("agent_evaluation_record", "agent_evaluation_dataset", "agent_step", "agent_action", "agent_run", "agent_runtime_state", "chat_history", "platform_job", "it_business_receipt")) {
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

    @Test void migrationsAreValidatedOnMySqlAndReachV35() {
        assertTrue(jdbc.queryForObject("SELECT VERSION()", String.class).startsWith("8."));
        assertEquals("35", jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1", String.class));
        assertEquals(35, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class));
        assertEquals(0, count("agent_run"));
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
        try (var old = leases.claim(run.getRunKey(), 1L)) {
            var scoped = store.fenced(old.fence());
            scoped.saveIfVersion("1", run.getRunKey(), "agent_state", state, 0);
            expire(run.getId()); assertEquals(1, leases.interruptExpired());
            var recovery = context.getBean(AgentRunRecoveryService.class);
            var receipt = recovery.request(run.getRunKey(), 1L);
            var jobs = context.getBean(PlatformJobService.class);
            jdbc.update("UPDATE platform_job SET status='RUNNING',lease_owner='worker-new',lease_until=TIMESTAMPADD(SECOND,120,NOW()) WHERE id=?", receipt.jobId());
            assertTrue(recovery.begin(new AgentRunRecoveryService.Payload(run.getRunKey(), receipt.generation()), jobs.get(receipt.jobId())));
            try (var next = leases.claim(run.getRunKey(), 1L)) {
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
            context.getBean(AgentRunLifecycle.class).complete(new AgentRunLifecycle.Context(lease.fence(), null, null),
                    new AgentRunLifecycle.Progress(1, 4, 2, 6), "ticket-42");
        }
        var command = new cn.sdh.backend.agent.evaluation.AgentEvaluationData.RecordRequest(dataset.id(), "ticket", run.getRunKey());
        var ids = simultaneous(() -> evaluation.record(command, 1L).id());
        assertEquals(ids.getFirst(), ids.getLast()); assertEquals(1, count("agent_evaluation_record"));
        String snapshot = evaluation.detail(ids.getFirst(), 1L).runSnapshotJson();
        assertEquals("ticket-42", com.alibaba.fastjson2.JSON.parseObject(snapshot).getString("answer"));
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
