package cn.sdh.backend.agent;

import cn.sdh.backend.agent.runtime.JdbcAgentStateStore;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class JdbcAgentStateStoreTest {
    private JdbcAgentStateStore store;
    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE agent_run(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE agent_action(id BIGINT PRIMARY KEY)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V31__agentscope_runtime.sql")).execute(source);
        store = new JdbcAgentStateStore(jdbc);
    }

    @Test void checkpointsSurviveNewStoreInstanceAndKeepMessageTypes() {
        var state = AgentState.builder().userId("7").sessionId("run-1").build();
        state.contextMutable().add(new UserMessage("保留上下文"));
        store.saveIfVersion("7", "run-1", "agent_state", state, 0);
        AgentState restored = store.get("7", "run-1", "agent_state", AgentState.class).orElseThrow();
        assertEquals("保留上下文", restored.getContext().getFirst().getTextContent());
        assertTrue(store.get("8", "run-1", "agent_state", AgentState.class).isEmpty());
    }

    @Test void rejectsStaleInsertAndStaleUpdates() {
        var state = AgentState.builder().userId("7").sessionId("run-1").build();
        long version = store.saveIfVersion("7", "run-1", "agent_state", state, 0);
        assertThrows(ConcurrentSessionModificationException.class,
                () -> store.saveIfVersion("7", "run-1", "agent_state", state, 0));
        assertEquals(version + 1, store.saveIfVersion("7", "run-1", "agent_state", state, version));
        assertThrows(ConcurrentSessionModificationException.class,
                () -> store.saveIfVersion("7", "run-1", "agent_state", state, version));
    }
}
