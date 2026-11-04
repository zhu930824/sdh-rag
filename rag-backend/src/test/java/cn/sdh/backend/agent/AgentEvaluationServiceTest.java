package cn.sdh.backend.agent;

import cn.sdh.backend.agent.evaluation.*;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.util.*;
import static cn.sdh.backend.agent.evaluation.AgentEvaluationData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentEvaluationServiceTest {
    @Configuration @EnableTransactionManagement static class Transactions {}
    AnnotationConfigApplicationContext context;
    SingleConnectionDataSource database;
    JdbcTemplate jdbc;
    RunStateService runs;
    ChatHistoryMapper histories;
    AgentEvaluationService service;
    AgentExecutionAttemptService attempts;

    @BeforeEach void setup() {
        database = new SingleConnectionDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V35__agent_evaluation_archives.sql")).execute(database);
        jdbc = new JdbcTemplate(database); runs = mock(RunStateService.class); histories = mock(ChatHistoryMapper.class);
        attempts = mock(AgentExecutionAttemptService.class);
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean(JdbcTemplate.class, () -> jdbc);
        context.registerBean(RunStateService.class, () -> runs);
        context.registerBean(ChatHistoryMapper.class, () -> histories);
        context.registerBean(AgentExecutionAttemptService.class, () -> attempts);
        context.registerBean(org.springframework.transaction.PlatformTransactionManager.class, () -> new DataSourceTransactionManager(database));
        context.register(AgentEvaluationService.class); context.refresh(); service = context.getBean(AgentEvaluationService.class);
    }
    @AfterEach void close() { cn.sdh.backend.common.context.UserContext.clear(); context.close(); database.destroy(); }

    @Test void importCreatesImmutableVersionsAndOwnerScopedLists() {
        var first = service.importDataset(new ImportRequest("售后", null, List.of(scenario("ticket"))), 7L);
        var second = service.importDataset(new ImportRequest("售后 v2", first.id(), List.of(scenario("refund"))), 7L);
        assertEquals(1, first.version()); assertEquals(2, second.version()); assertEquals(first.id(), second.parentId());
        assertEquals("ticket", service.dataset(first.id(), 7L).scenarios().getFirst().id());
        assertNotEquals(first.sha256(), second.sha256());
        assertEquals(2, service.datasets(7L).size()); assertTrue(service.datasets(8L).isEmpty());
        assertCode(404, () -> service.dataset(first.id(), 8L));
        assertCode(404, () -> service.importDataset(new ImportRequest("越权", first.id(), List.of(scenario("x"))), 8L));
        assertCode(409, () -> service.importDataset(new ImportRequest("分叉", first.id(), List.of(scenario("x"))), 7L));
    }

    @Test void invalidImportIsRejectedBeforeAnyWrite() {
        assertCode(401, () -> service.datasets(null));
        assertCode(400, () -> service.importDataset(new ImportRequest("", null, List.of(scenario("a"))), 7L));
        assertCode(400, () -> service.importDataset(new ImportRequest("重复", null, List.of(scenario("a"), scenario("a"))), 7L));
        assertCode(400, () -> service.importDataset(new ImportRequest("空", null, List.of()), 7L));
        var conflict = new Scenario("bad", "任务", null, List.of("delete"), List.of("delete"), "拒绝", List.of(), Risk.HIGH);
        assertCode(400, () -> service.importDataset(new ImportRequest("冲突", null, List.of(conflict)), 7L));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM agent_evaluation_dataset", Integer.class));
    }

    @Test void recordingFreezesOutcomeAndTraceWithoutReexecutingAnything() {
        var dataset = service.importDataset(new ImportRequest("售后", null, List.of(scenario("ticket"))), 7L);
        AgentRun run = run("COMPLETED");
        run.setRetrievalSnapshotJson("{\"scope\":\"RETRIEVED_CANDIDATES\",\"chunks\":[{\"content\":\"original evidence\",\"indexRevision\":3}]}");
        ChatHistory history = new ChatHistory(); history.setId(20L); history.setUserId(7L); history.setAgentRunKey("run-1"); history.setAnswer("工单已创建");
        when(histories.selectById(20L)).thenReturn(history);
        var request = new RecordRequest(dataset.id(), "ticket", "run-1");
        var record = service.record(request, 7L);
        assertEquals("RECORDED", record.status()); assertEquals(dataset.sha256(), record.datasetSha256());
        assertEquals("工单已创建", JSON.parseObject(record.runSnapshotJson()).getString("answer"));
        assertEquals("2.0.3", JSON.parseObject(record.runSnapshotJson()).getString("engineVersion"));
        assertEquals("UNKNOWN", JSON.parseObject(record.runSnapshotJson()).getString("modelConfigurationVersion"));
        assertEquals("ticket", JSON.parseObject(record.scenarioJson()).getString("id"));
        assertEquals(3, JSON.parseObject(record.runSnapshotJson()).getJSONObject("retrievalSnapshot").getJSONArray("chunks").getJSONObject(0).getIntValue("indexRevision"));
        run.setRetrievalSnapshotJson("{\"chunks\":[]}");
        history.setAnswer("后续修改"); run.setEngineVersion("future");
        assertEquals(record.runSnapshotJson(), service.detail(record.id(), 7L).runSnapshotJson());
        assertEquals(record.id(), service.record(request, 7L).id());
        assertEquals(1, service.records(dataset.id(), 7L).size());
        assertCode(404, () -> service.detail(record.id(), 8L));
        assertCode(404, () -> service.records(dataset.id(), 8L));
        verify(runs, times(1)).getByRunKey("run-1");
        verify(runs, never()).save(any());
    }

    @Test void rejectsForeignOrNonterminalRunsAndUnknownScenarios() {
        var dataset = service.importDataset(new ImportRequest("售后", null, List.of(scenario("ticket"))), 7L);
        var request = new RecordRequest(dataset.id(), "ticket", "run-1");
        for (String state : List.of("RUNNING", "WAITING_ACTION", "WAITING_APPROVAL", "INTERRUPTED")) {
            run(state); assertCode(409, () -> service.record(request, 7L));
        }
        run("FAILED").setUserId(8L); assertCode(404, () -> service.record(request, 7L));
        assertCode(404, () -> service.record(new RecordRequest(dataset.id(), "missing", "run-1"), 7L));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM agent_evaluation_record", Integer.class));
    }

    @Test void insertFailureRollsBackAndInvalidHistoryCannotLeak() {
        var dataset = service.importDataset(new ImportRequest("售后", null, List.of(scenario("ticket"))), 7L);
        run("COMPLETED");
        ChatHistory foreign = new ChatHistory(); foreign.setUserId(8L); foreign.setAnswer("private-answer");
        when(histories.selectById(20L)).thenReturn(foreign);
        assertCode(409, () -> service.record(new RecordRequest(dataset.id(), "ticket", "run-1"), 7L));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM agent_evaluation_record", Integer.class));
        jdbc.execute("ALTER TABLE agent_evaluation_dataset ADD CONSTRAINT reject_second CHECK (version < 2)");
        assertThrows(RuntimeException.class, () -> service.importDataset(new ImportRequest("v2", dataset.id(), List.of(scenario("b"))), 7L));
        assertEquals(1, service.datasets(7L).size());
    }

    @Test void traceCapsKeepValidJsonAndExplicitTruncationWithoutInventedVersions() {
        var dataset = service.importDataset(new ImportRequest("售后", null, List.of(scenario("ticket"))), 7L);
        run("FAILED").setHistoryId(null);
        List<AgentStep> steps = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            AgentStep step = new AgentStep(); step.setSeq(i + 1); step.setInputJson("参".repeat(3000)); step.setOutputJson("果".repeat(7000)); steps.add(step);
        }
        when(runs.stepsOf(10L)).thenReturn(steps);
        var record = service.record(new RecordRequest(dataset.id(), "ticket", "run-1"), 7L);
        var snapshot = JSON.parseObject(record.runSnapshotJson());
        assertTrue(snapshot.getBooleanValue("truncated")); assertEquals(60, snapshot.getIntValue("recordedStepCount"));
        assertEquals(50, snapshot.getJSONArray("steps").size());
        assertEquals("UNKNOWN", snapshot.getString("toolDefinitionVersion"));
        assertEquals(64, record.snapshotSha256().length());
        assertTrue(JSON.isValidObject(record.runSnapshotJson()));
    }
    @Test void completeAndPartialExecutionHistoriesCannotBeConfusedWithOneConfiguration() {
        var dataset = service.importDataset(new ImportRequest("版本", null, List.of(scenario("ticket"))), 7L);
        var run = run("COMPLETED"); run.setHistoryId(null); run.setExecutionEpoch(2L);
        when(attempts.list(10L, 7L)).thenReturn(List.of(attempt(1, "model-a"), attempt(2, "model-b")));
        var archive = service.record(new RecordRequest(dataset.id(), "ticket", "run-1"), 7L);
        var snapshot = JSON.parseObject(archive.runSnapshotJson());
        assertEquals("COMPLETE", snapshot.getString("configurationCoverage"));
        assertEquals("MULTIPLE", snapshot.getString("modelConfigurationVersion"));
        assertEquals(2, snapshot.getJSONArray("executionAttempts").size());
        var second = service.importDataset(new ImportRequest("部分历史", null, List.of(scenario("ticket"))), 7L);
        when(attempts.list(10L, 7L)).thenReturn(List.of(attempt(2, "model-b")));
        var partial = JSON.parseObject(service.record(new RecordRequest(second.id(), "ticket", "run-1"), 7L).runSnapshotJson());
        assertEquals("PARTIAL", partial.getString("configurationCoverage"));
        assertEquals("UNKNOWN", partial.getString("modelConfigurationVersion"));
        assertEquals("UNKNOWN", partial.getString("knowledgePublicationVersion"));
    }
    static AgentExecutionAttemptService.Attempt attempt(long epoch, String version) {
        return new AgentExecutionAttemptService.Attempt(epoch, version, JSON.toJSONString(Map.of("modelConfigurationVersion", version,
                "promptVersion", "prompt", "toolDefinitionVersion", "tools")), java.time.LocalDateTime.now());
    }

    @Test void validatesImportBoundsAndCanonicalIdentifiers() {
        for (String id : List.of("Ticket", "a/b", "a".repeat(65))) {
            assertCode(400, () -> service.importDataset(new ImportRequest("非法 ID", null, List.of(scenario(id))), 7L));
        }
        assertCode(400, () -> service.importDataset(new ImportRequest("太多", null, Collections.nCopies(101, scenario("ticket"))), 7L));
        var tooLong = new Scenario("a", "长".repeat(10001), null, List.of(), List.of(), "完成", List.of(), Risk.LOW);
        assertCode(400, () -> service.importDataset(new ImportRequest("长任务", null, List.of(tooLong)), 7L));
        List<Scenario> large = new ArrayList<>();
        for (int i = 0; i < 40; i++) large.add(new Scenario("case-" + i, "长".repeat(10000), null, List.of(), List.of(), "完成", List.of(), Risk.LOW));
        assertCode(400, () -> service.importDataset(new ImportRequest("大文件", null, large), 7L));
        assertEquals(0, service.datasets(7L).size());
    }

    @Test void httpImportsPrivateVersionsAndRejectsMalformedRiskAtTheBoundary() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new cn.sdh.backend.controller.AgentEvaluationController(service))
                .setControllerAdvice(new cn.sdh.backend.common.exception.GlobalExceptionHandler()).build();
        mvc.perform(get("/api/agent/evaluation/datasets")).andExpect(jsonPath("$.code").value(401));
        var actor = new cn.sdh.backend.common.context.UserContext(); actor.setUserId(7L); cn.sdh.backend.common.context.UserContext.setUserContext(actor);
        mvc.perform(post("/api/agent/evaluation/datasets").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(new ImportRequest("API 版本", null, List.of(scenario("ticket"))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.scenarios[0].id").value("ticket"));
        mvc.perform(post("/api/agent/evaluation/datasets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"bad\",\"scenarios\":[{\"id\":\"ticket\",\"risk\":\"DANGER\"}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/agent/evaluation/records")).andExpect(status().isBadRequest());
        actor.setUserId(8L);
        mvc.perform(get("/api/agent/evaluation/datasets")).andExpect(jsonPath("$.data.length()").value(0));
    }

    AgentRun run(String status) {
        AgentRun run = new AgentRun(); run.setId(10L); run.setUserId(7L); run.setRunKey("run-1"); run.setHistoryId(20L);
        run.setEngine("agentscope"); run.setEngineVersion("2.0.3"); run.setStatus(status); run.setQuestion("创建售后工单");
        when(runs.getByRunKey("run-1")).thenReturn(run); when(runs.stepsOf(10L)).thenReturn(List.of());
        return run;
    }
    static Scenario scenario(String id) { return new Scenario(id, "创建售后工单", null, List.of("ticket"), List.of("delete"), "工单创建成功", List.of("核对订单"), Risk.MEDIUM); }
    static void assertCode(int code, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(code, assertThrows(BusinessException.class, operation).getCode());
    }
}
