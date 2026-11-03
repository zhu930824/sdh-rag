package cn.sdh.backend.agent;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.service.PlatformJobService;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.List;
import java.util.Objects;

/** Owner-triggered, durable recovery. Every generation has one job and starts at most once. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentRunRecoveryService {
    private final JdbcTemplate jdbc;
    private final PlatformJobService jobs;
    private final PlatformTransactionManager transactionManager;

    public record Payload(String runKey, long generation) {}
    public record Receipt(Long jobId, long generation, String status) {}
    public record Diagnosis(boolean canRecover, String reason) {}
    private record Row(long id, Long userId, String status, long epoch, String owner, String engine,
                       long generation, Long jobId, AgentRunBudget budget) {}

    public Diagnosis diagnose(String runKey, Long actor) {
        Row row = load(runKey, false);
        if (!Objects.equals(row.userId(), actor)) return new Diagnosis(false, "仅运行发起人可恢复");
        var decision = decision(row);
        return new Diagnosis(decision.allowed(), decision.reason());
    }

    @Transactional(rollbackFor = Exception.class)
    public Receipt request(String runKey, Long actor) {
        requireActor(actor);
        Row row = load(runKey, true);
        requireOwner(row, actor);
        if ("RECOVERY_QUEUED".equals(row.status())) return new Receipt(row.jobId(), row.generation(), row.status());
        var decision = decision(row);
        if (!decision.allowed()) throw new BusinessException(409, decision.reason());
        long generation = row.generation() + 1;
        PlatformJob job = jobs.enqueue(new PlatformJobService.EnqueueCommand(
                "agent-recover:" + row.id() + ":" + generation, "AGENT_RECOVER", "AGENT_RUN", row.id(),
                JSON.toJSONString(new Payload(runKey, generation)), 5, 1, actor));
        jdbc.update("""
                UPDATE agent_run SET status='RECOVERY_QUEUED',recovery_generation=?,recovery_job_id=?,
                  error_message=NULL,completed_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?
                """, generation, job.getId(), row.id());
        publishNotice(row.id(), "任务恢复已进入队列，正在等待重新执行。");
        return new Receipt(job.getId(), generation, "RECOVERY_QUEUED");
    }

    /** Check the persisted job identity too: a stale payload must never activate a later generation. */
    @Transactional(rollbackFor = Exception.class)
    public boolean begin(Payload payload, PlatformJob job) {
        Row row = load(payload.runKey(), true);
        requireOwner(row, job.getCreatedBy());
        if (!Objects.equals(row.jobId(), job.getId()) || row.generation() != payload.generation()
                || !"RECOVERY_QUEUED".equals(row.status())) return false;
        PlatformJob stored = jobs.get(job.getId());
        if (stored == null || !"AGENT_RECOVER".equals(stored.getJobType())
                || !Objects.equals(stored.getResourceId(), row.id()) || !Objects.equals(stored.getCreatedBy(), row.userId())
                || !PlatformJob.RUNNING.equals(stored.getStatus()) || !Objects.equals(stored.getLeaseOwner(), job.getLeaseOwner())) {
            throw new BusinessException(409, "恢复任务已失去执行权");
        }
        // Re-evaluate under the run lock, rather than trusting the earlier diagnosis or HTTP request.
        var decision = AgentRunRecoveryPolicy.decide(new AgentRunRecoveryPolicy.Facts(
                "INTERRUPTED", row.epoch(), row.owner() != null, row.engine(), hasActions(row.id()), row.budget()));
        if (!decision.allowed()) throw new BusinessException(409, decision.reason());
        jdbc.update("DELETE FROM agent_runtime_state WHERE user_id=? AND session_id=?", row.userId().toString(), payload.runKey());
        jdbc.update("""
                UPDATE agent_run SET status='RUNNING',messages_json=NULL,pending_action_id=NULL,error_message=NULL,
                  completed_at=NULL,heartbeat_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?
                """, row.id());
        publishNotice(row.id(), "任务正在恢复执行。");
        return true;
    }

    /** Preparation/cancellation before a lease is claimed must not leave a RUNNING placeholder forever. */
    @Transactional(rollbackFor = Exception.class)
    public void failBeforeClaim(Payload payload, Long jobId) {
        Row row = load(payload.runKey(), true);
        if (row.generation() != payload.generation() || !Objects.equals(row.jobId(), jobId)
                || !"RUNNING".equals(row.status()) || row.owner() != null) return;
        PlatformJob job = jobs.get(jobId);
        if (job != null && (PlatformJob.CANCEL_REQUESTED.equals(job.getStatus()) || PlatformJob.CANCELLED.equals(job.getStatus()))) {
            finish(row, "CANCELLED", "任务已取消");
        } else {
            interrupt(row, "恢复执行未能启动，请检查模型配置或任务状态后重新申请。");
        }
    }

    /** A cancelled/dead recovery job is not a reason to replay automatically. Return control to its owner. */
    @Scheduled(fixedDelayString = "${xushu.agent.recovery-delay-ms:15000}")
    public void reconcileQueue() {
        List<String> candidates = jdbc.queryForList("""
                SELECT r.run_key FROM agent_run r LEFT JOIN platform_job j ON j.id=r.recovery_job_id
                WHERE r.status='RECOVERY_QUEUED' AND (j.id IS NULL OR j.status IN ('CANCELLED','DEAD_LETTER','FAILED','SUCCEEDED'))
                ORDER BY r.id LIMIT 100
                """, String.class);
        var transactions = new TransactionTemplate(transactionManager);
        for (String key : candidates) {
            try {
                transactions.executeWithoutResult(status -> {
                    Row row = load(key, true);
                    if (!"RECOVERY_QUEUED".equals(row.status())) return;
                    PlatformJob job = row.jobId() == null ? null : jobs.get(row.jobId());
                    if (job != null && !List.of("CANCELLED", "DEAD_LETTER", "FAILED", "SUCCEEDED").contains(job.getStatus())) return;
                    interrupt(row, "恢复任务已停止，尚未开始新的执行，可重新申请恢复。");
                });
            } catch (RuntimeException failure) { log.warn("恢复队列核对失败 runKey={}", key); }
        }
    }

    private AgentRunRecoveryPolicy.Decision decision(Row row) {
        return AgentRunRecoveryPolicy.decide(new AgentRunRecoveryPolicy.Facts(
                row.status(), row.epoch(), row.owner() != null, row.engine(), hasActions(row.id()), row.budget()));
    }
    private boolean hasActions(long id) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM agent_action WHERE run_id=?", Long.class, id);
        return count != null && count > 0;
    }
    private Row load(String key, boolean lock) {
        List<Row> rows = jdbc.query("""
                SELECT id,user_id,status,execution_epoch,lease_owner,engine,recovery_generation,recovery_job_id,
                  step_count,max_steps,total_tokens,prompt_tokens,completion_tokens
                FROM agent_run WHERE run_key=?
                """ + (lock ? " FOR UPDATE" : ""), (rs, n) -> new Row(rs.getLong(1), rs.getObject(2, Long.class),
                rs.getString(3), rs.getLong(4), rs.getString(5), rs.getString(6), rs.getLong(7), rs.getObject(8, Long.class),
                AgentRunBudget.from(rs.getObject(9, Integer.class), rs.getObject(10, Integer.class), rs.getObject(11, Integer.class),
                        rs.getObject(12, Integer.class), rs.getObject(13, Integer.class))), key);
        if (rows.isEmpty()) throw new BusinessException(404, "Agent 运行不存在");
        return rows.getFirst();
    }
    private void interrupt(Row row, String reason) {
        finish(row, "INTERRUPTED", reason);
    }
    private void finish(Row row, String state, String reason) {
        jdbc.update("UPDATE agent_run SET status=?,error_message=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", state, reason, row.id());
        publishNotice(row.id(), reason);
    }
    private void publishNotice(long runId, String text) {
        Long history = jdbc.queryForObject("SELECT history_id FROM agent_run WHERE id=?", Long.class, runId);
        if (history != null && jdbc.update("UPDATE chat_history SET answer=?,agent_run_key=(SELECT run_key FROM agent_run WHERE id=?),update_time=CURRENT_TIMESTAMP WHERE id=?",
                text, runId, history) != 1) {
            throw new IllegalStateException("恢复状态回答更新失败");
        }
    }
    private void requireActor(Long actor) { if (actor == null) throw new BusinessException(401, "请先登录"); }
    private void requireOwner(Row row, Long actor) {
        requireActor(actor);
        if (!actor.equals(row.userId())) throw new BusinessException(403, "仅运行发起人可恢复");
    }
}
