package cn.sdh.backend.agent;

import cn.sdh.backend.entity.*;
import cn.sdh.backend.service.factory.ModelProvider;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentExecutionAttemptServiceTest {
    SingleConnectionDataSource source;
    JdbcTemplate jdbc;
    RunExecutionLeaseService leases;
    AgentExecutionAttemptService service;
    @BeforeEach void setup() {
        source = new SingleConnectionDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V36__agent_execution_attempt_snapshots.sql")).execute(source);
        jdbc = new JdbcTemplate(source); leases = mock(RunExecutionLeaseService.class);
        when(leases.guarded(any(), any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(1)).get());
        service = new AgentExecutionAttemptService(jdbc, leases);
    }
    @AfterEach void close() { source.destroy(); }

    @Test void snapshotsAreIdempotentPrivateAndNeverPersistCredentialsOrPromptBodies() {
        var p = prepared(1, "original-model", "private-prompt", "POST");
        var run = run(); run.setEngineVersion("original-old-version");
        var first = service.capture(p, run);
        p.tools().getFirst().setHttpMethod("DELETE");
        assertEquals(first, service.capture(p, run));
        assertEquals(1, service.list(10L, 7L).size()); assertTrue(service.list(10L, 8L).isEmpty());
        var json = JSON.parseObject(first.snapshotJson());
        var engine = json.getJSONObject("configuration").getJSONObject("engine");
        assertEquals("original-old-version", engine.getString("originalRunVersion"));
        assertNotEquals("UNKNOWN", engine.getString("version"));
        assertNotEquals("original-old-version", engine.getString("version"));
        assertEquals("original-model", json.getJSONObject("configuration").getJSONObject("model").getString("modelId"));
        for (String forbidden : List.of("private-prompt", "secret-key", "private-evidence", "credential-ref", "https://private.example")) {
            assertFalse(first.snapshotJson().contains(forbidden), forbidden);
        }
        assertTrue(json.getJSONArray("changedSections").isEmpty());
        assertEquals(64, first.configurationSha256().length());
    }
    @Test void resumesRetainEachVersionAndExplainChangedSections() {
        service.capture(prepared(1, "model-a", "prompt-a", "GET"), run());
        var next = service.capture(prepared(2, "model-b", "prompt-b", "POST"), run());
        var json = JSON.parseObject(next.snapshotJson());
        assertEquals(1, json.getLongValue("previousEpoch"));
        assertEquals(Set.of("model", "prompt", "tools"), new HashSet<>(json.getList("changedSections", String.class)));
        assertEquals(2, service.list(10L, 7L).size());
        assertThrows(cn.sdh.backend.common.exception.BusinessException.class,
                () -> service.capture(prepared(2, "model-c", "prompt-b", "POST"), run()));
        assertEquals(2, service.list(10L, 7L).size());
    }
    @Test void aLostFenceCannotCreateAnAttempt() {
        doThrow(new cn.sdh.backend.common.exception.BusinessException(409, "lost fence")).when(leases).guarded(any(), any());
        assertThrows(cn.sdh.backend.common.exception.BusinessException.class,
                () -> service.capture(prepared(1, "model", "prompt", "GET"), run()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM agent_execution_attempt", Integer.class));
    }
    @Test void mismatchedRunOwnershipIsRejectedBeforeWriting() {
        var run = run(); run.setUserId(8L);
        assertEquals(409, assertThrows(cn.sdh.backend.common.exception.BusinessException.class,
                () -> service.capture(prepared(1, "model", "prompt", "GET"), run)).getCode());
        verify(leases, never()).guarded(any(), any());
    }

    static AgentRun run() {
        AgentRun run = new AgentRun(); run.setId(10L); run.setRunKey("run-1"); run.setUserId(7L);
        run.setEngine("agentscope"); run.setEngineVersion("2.0.3"); return run;
    }
    static AgentAskService.Prepared prepared(long epoch, String modelId, String system, String method) {
        ModelConfig model = new ModelConfig(); model.setId(1L); model.setModelId(modelId); model.setProvider("openai");
        model.setApiKey("secret-key"); model.setBaseUrl("https://private.example?api_key=secret-key");
        AgentTool tool = new AgentTool(); tool.setId(3L); tool.setCode("ticket"); tool.setHttpMethod(method);
        tool.setEndpoint("https://private.example"); tool.setCredentialRef("credential-ref");
        var lease = mock(RunExecutionLeaseService.Lease.class);
        when(lease.fence()).thenReturn(new RunExecutionLeaseService.Fence(10, "run-1", 7L, "owner", epoch));
        return new AgentAskService.Prepared("run-1", 10L, 20L, "session", "question", 7L, null,
                new AgentExecutionPreparation.ModelBinding(model, ModelProvider.OPENAI, null),
                system, false, null, null, null, null, "private-evidence", List.of(tool), 8, epoch > 1, null, lease);
    }
}
