package cn.sdh.backend.agent.runtime;

import io.agentscope.core.state.*;
import io.agentscope.core.util.JsonUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import cn.sdh.backend.agent.RunExecutionLeaseService;

import java.util.*;

/** Durable SDK state with optimistic concurrency; database failures must propagate. */
@Component
public class JdbcAgentStateStore implements AgentStateStore {
    private final JdbcTemplate jdbc;
    private final RunExecutionLeaseService leases;
    private final RunExecutionLeaseService.Fence fence;
    public JdbcAgentStateStore(JdbcTemplate jdbc) { this(jdbc, null, null); }
    @Autowired public JdbcAgentStateStore(JdbcTemplate jdbc, RunExecutionLeaseService leases) { this(jdbc, leases, null); }
    private JdbcAgentStateStore(JdbcTemplate jdbc, RunExecutionLeaseService leases, RunExecutionLeaseService.Fence fence) {
        this.jdbc = jdbc; this.leases = leases; this.fence = fence;
    }
    public JdbcAgentStateStore fenced(RunExecutionLeaseService.Fence fence) {
        return new JdbcAgentStateStore(jdbc, Objects.requireNonNull(leases), Objects.requireNonNull(fence));
    }
    private <T> T write(String user, String session, java.util.function.Supplier<T> operation) {
        if (fence == null) return operation.get();
        if (!fence.userId().toString().equals(user) || !fence.runKey().equals(session)) {
            throw new cn.sdh.backend.common.exception.BusinessException(409, "检查点与执行租约不匹配");
        }
        return leases.guarded(fence, operation);
    }

    @Override public boolean supportsVersioning() { return true; }

    @Override public <T extends State> VersionedState<T> getVersioned(
            String user, String session, String key, Class<T> type) {
        List<VersionedState<T>> rows = jdbc.query(
                "SELECT payload, state_version FROM agent_runtime_state WHERE user_id=? AND session_id=? AND state_key=?",
                (rs, row) -> new VersionedState<>(decode(rs.getString(1), type), rs.getLong(2)), user, session, key);
        return rows.isEmpty() ? new VersionedState<>(null, 0) : rows.getFirst();
    }

    @Override public long saveIfVersion(String user, String session, String key, State state, long expected) {
        return savePayloadIfVersion(user, session, key, encode(state), expected);
    }

    private long savePayloadIfVersion(String user, String session, String key, String payload, long expected) {
        return write(user, session, () -> savePayload(user, session, key, payload, expected));
    }

    private long savePayload(String user, String session, String key, String payload, long expected) {
        if (expected == UNVERSIONED) {
            List<Long> versions = jdbc.queryForList("SELECT state_version FROM agent_runtime_state WHERE user_id=? AND session_id=? AND state_key=?",
                    Long.class, user, session, key);
            return savePayload(user, session, key, payload, versions.isEmpty() ? 0 : versions.getFirst());
        }
        if (expected == 0) {
            try {
                jdbc.update("INSERT INTO agent_runtime_state (user_id,session_id,state_key,state_version,payload) VALUES (?,?,?,1,?)",
                        user, session, key, payload);
                return 1;
            } catch (DuplicateKeyException duplicate) {
                throw new ConcurrentSessionModificationException(user, session, key, expected);
            }
        }
        int updated = jdbc.update("UPDATE agent_runtime_state SET payload=?, state_version=state_version+1, updated_at=CURRENT_TIMESTAMP "
                        + "WHERE user_id=? AND session_id=? AND state_key=? AND state_version=?",
                payload, user, session, key, expected);
        if (updated != 1) throw new ConcurrentSessionModificationException(user, session, key, expected);
        return expected + 1;
    }

    @Override public void save(String user, String session, String key, State state) {
        VersionedState<State> current = getVersioned(user, session, key, State.class);
        saveIfVersion(user, session, key, state, current.version());
    }

    @Override public void save(String user, String session, String key, List<? extends State> states) {
        List<Long> versions = jdbc.queryForList("SELECT state_version FROM agent_runtime_state WHERE user_id=? AND session_id=? AND state_key=?",
                Long.class, user, session, key);
        savePayloadIfVersion(user, session, key, JsonUtils.getJsonCodec().toJson(states),
                versions.isEmpty() ? 0 : versions.getFirst());
    }

    @Override public <T extends State> Optional<T> get(String user, String session, String key, Class<T> type) {
        return Optional.ofNullable(getVersioned(user, session, key, type).value());
    }

    @Override public <T extends State> List<T> getList(String user, String session, String key, Class<T> type) {
        List<String> payloads = jdbc.queryForList("SELECT payload FROM agent_runtime_state WHERE user_id=? AND session_id=? AND state_key=?",
                String.class, user, session, key);
        if (payloads.isEmpty()) return List.of();
        List<?> values = JsonUtils.getJsonCodec().fromJson(payloads.getFirst(), List.class);
        return values.stream().map(value -> JsonUtils.getJsonCodec().convertValue(value, type)).toList();
    }

    @Override public boolean exists(String user, String session) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM agent_runtime_state WHERE user_id=? AND session_id=?",
                Long.class, user, session);
        return count != null && count > 0;
    }

    @Override public void delete(String user, String session) {
        write(user, session, () -> jdbc.update("DELETE FROM agent_runtime_state WHERE user_id=? AND session_id=?", user, session));
    }
    @Override public void delete(String user, String session, String key) {
        write(user, session, () -> jdbc.update("DELETE FROM agent_runtime_state WHERE user_id=? AND session_id=? AND state_key=?", user, session, key));
    }
    @Override public Set<String> listSessionIds(String user) {
        return new HashSet<>(jdbc.queryForList("SELECT DISTINCT session_id FROM agent_runtime_state WHERE user_id=?", String.class, user));
    }

    private String encode(State state) {
        if (state instanceof AgentState agentState) return agentState.toJson();
        return JsonUtils.getJsonCodec().toJson(state);
    }
    private <T extends State> T decode(String json, Class<T> type) {
        if (type == AgentState.class || type == State.class) return type.cast(AgentState.fromJsonString(json));
        return JsonUtils.getJsonCodec().fromJson(json, type);
    }
}
