package cn.sdh.backend.agent;

import cn.sdh.backend.entity.AgentRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import cn.sdh.backend.common.exception.BusinessException;
import com.alibaba.fastjson2.JSON;

@Service
public class AgentExecutionAttemptService {
    public record Attempt(long epoch, String configurationSha256, String snapshotJson, LocalDateTime createdAt) {}
    private final JdbcTemplate jdbc;
    private final RunExecutionLeaseService leases;
    public AgentExecutionAttemptService(JdbcTemplate jdbc, RunExecutionLeaseService leases) { this.jdbc = jdbc; this.leases = leases; }
    public Attempt capture(AgentAskService.Prepared p, AgentRun run) {
        var fence = p.lease().fence();
        if (!Objects.equals(run.getId(), fence.runId()) || !Objects.equals(run.getUserId(), fence.userId())
                || !Objects.equals(run.getRunKey(), fence.runKey()) || !Objects.equals(p.runId(), run.getId())
                || !Objects.equals(p.userId(), run.getUserId()) || !Objects.equals(p.runKey(), run.getRunKey())) {
            throw new BusinessException(409, "执行快照与运行租约不匹配");
        }
        var configuration = AgentExecutionConfiguration.capture(p, run);
        String hash = AgentExecutionConfiguration.version(configuration);
        return leases.guarded(fence, () -> {
            var previous = list(run.getId(), p.userId());
            var existing = previous.stream().filter(a -> a.epoch() == fence.epoch()).findFirst();
            if (existing.isPresent()) return same(existing.get(), hash);
            Attempt last = previous.isEmpty() ? null : previous.getLast();
            var before = last == null ? null : JSON.parseObject(last.snapshotJson()).getJSONObject("configuration");
            List<String> changed = before == null ? List.of() : configuration.keySet().stream()
                    .filter(key -> !AgentExecutionConfiguration.version(configuration.get(key)).equals(AgentExecutionConfiguration.version(before.get(key)))).toList();
            LocalDateTime now = LocalDateTime.now();
            var snapshot = AgentExecutionConfiguration.map("schemaVersion", 1, "executionEpoch", fence.epoch(),
                    "previousEpoch", last == null ? null : last.epoch(), "configuration", configuration,
                    "configurationSha256", hash, "changedSections", changed, "capturedAt", now.toString(),
                    "modelConfigurationVersion", AgentExecutionConfiguration.version(configuration.get("model")),
                    "promptVersion", AgentExecutionConfiguration.version(configuration.get("prompt")),
                    "toolDefinitionVersion", AgentExecutionConfiguration.version(configuration.get("tools")));
            String payload = JSON.toJSONString(snapshot);
            if (payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1024 * 1024) throw new BusinessException(400, "执行配置快照超过 1 MiB 上限");
            try {
                jdbc.update("INSERT INTO agent_execution_attempt (run_id,execution_epoch,run_key,user_id,configuration_sha256,snapshot_json,created_at) VALUES (?,?,?,?,?,?,?)",
                        run.getId(), fence.epoch(), run.getRunKey(), p.userId(), hash, payload, now);
            } catch (org.springframework.dao.DuplicateKeyException conflict) {
                // A duplicate may have committed outside a caller's older REPEATABLE READ snapshot.
                return same(current(run.getId(), fence.epoch(), p.userId()), hash);
            }
            return current(run.getId(), fence.epoch(), p.userId());
        });
    }
    public List<Attempt> list(Long runId, Long actor) {
        if (actor == null) throw new BusinessException(401, "请先登录");
        return jdbc.query("SELECT execution_epoch,configuration_sha256,snapshot_json,created_at FROM agent_execution_attempt WHERE run_id=? AND user_id=? ORDER BY execution_epoch",
                (rs, n) -> new Attempt(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toLocalDateTime()), runId, actor);
    }
    private Attempt same(Attempt existing, String hash) {
        if (!hash.equals(existing.configurationSha256())) throw new BusinessException(409, "同一执行批次不能覆盖配置快照");
        return existing;
    }
    private Attempt current(Long runId, long epoch, Long actor) {
        return jdbc.queryForObject("SELECT execution_epoch,configuration_sha256,snapshot_json,created_at FROM agent_execution_attempt WHERE run_id=? AND execution_epoch=? AND user_id=? FOR UPDATE",
                (rs, n) -> new Attempt(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toLocalDateTime()), runId, epoch, actor);
    }
}
