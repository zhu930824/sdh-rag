package cn.sdh.backend.agent;

import cn.sdh.backend.common.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** A database fence is checked under a row lock in the same short transaction as each write. */
@Service
public class RunExecutionLeaseService implements AutoCloseable {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ScheduledExecutorService heartbeats = Executors.newScheduledThreadPool(2, task -> {
        Thread thread = new Thread(task, "agent-run-heartbeat"); thread.setDaemon(true); return thread;
    });

    public RunExecutionLeaseService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }

    public record Fence(long runId, String runKey, Long userId, String owner, long epoch) {}

    public <T> T transaction(Supplier<T> write) { return transactions.execute(status -> write.get()); }

    public Lease claim(String runKey, Long userId) {
        return claim(runKey, userId, () -> {});
    }

    public Lease claim(String runKey, Long userId, Runnable ownerCheckpoint) {
        ownerCheckpoint.run();
        String owner = UUID.randomUUID().toString();
        Fence fence = transactions.execute(status -> {
            int changed = jdbc.update("""
                    UPDATE agent_run SET execution_epoch=execution_epoch+1, lease_owner=?,
                      lease_until=TIMESTAMPADD(SECOND,120,CURRENT_TIMESTAMP), heartbeat_at=CURRENT_TIMESTAMP
                    WHERE run_key=? AND user_id=? AND status='RUNNING' AND lease_owner IS NULL
                    """, owner, runKey, userId);
            if (changed != 1) throw lost();
            return jdbc.queryForObject("SELECT id,execution_epoch FROM agent_run WHERE run_key=?",
                    (row, n) -> new Fence(row.getLong(1), runKey, userId, owner, row.getLong(2)), runKey);
        });
        return new Lease(Objects.requireNonNull(fence), ownerCheckpoint);
    }

    public boolean renew(Fence fence) {
        return jdbc.update("""
                UPDATE agent_run SET lease_until=TIMESTAMPADD(SECOND,120,CURRENT_TIMESTAMP), heartbeat_at=CURRENT_TIMESTAMP
                WHERE id=? AND user_id=? AND lease_owner=? AND execution_epoch=?
                  AND status='RUNNING' AND lease_until>CURRENT_TIMESTAMP
                """, fence.runId(), fence.userId(), fence.owner(), fence.epoch()) == 1;
    }

    /** No HTTP/model calls may be placed inside this transaction. */
    public <T> T guarded(Fence fence, Supplier<T> write) {
        Objects.requireNonNull(fence, "运行写入必须携带执行租约");
        return transactions.execute(status -> {
            List<Long> owned = jdbc.queryForList("""
                    SELECT id FROM agent_run WHERE id=? AND run_key=? AND user_id=? AND status='RUNNING'
                      AND lease_owner=? AND execution_epoch=? AND lease_until>CURRENT_TIMESTAMP FOR UPDATE
                    """, Long.class, fence.runId(), fence.runKey(), fence.userId(), fence.owner(), fence.epoch());
            if (owned.isEmpty()) throw lost();
            return write.get();
        });
    }

    /** Scan candidates, then recheck under lock: a concurrent heartbeat wins without a false interruption. */
    public int interruptExpired() {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM agent_run WHERE status='RUNNING' AND execution_epoch>0
                  AND (lease_until<=CURRENT_TIMESTAMP OR (lease_owner IS NULL AND heartbeat_at<TIMESTAMPADD(SECOND,-120,CURRENT_TIMESTAMP)))
                ORDER BY id LIMIT 100
                """, Long.class);
        int changed = 0;
        for (Long id : ids) changed += Objects.requireNonNull(transactions.execute(status -> interrupt(id)));
        return changed;
    }

    private int interrupt(Long id) {
        var rows = jdbc.queryForList("""
                SELECT history_id,run_key FROM agent_run WHERE id=? AND status='RUNNING' AND execution_epoch>0
                  AND (lease_until<=CURRENT_TIMESTAMP OR (lease_owner IS NULL AND heartbeat_at<TIMESTAMPADD(SECOND,-120,CURRENT_TIMESTAMP)))
                FOR UPDATE
                """, id);
        if (rows.isEmpty()) return 0;
        String message = "任务执行已中断，请查看业务动作记录并核对已提交的操作。";
        jdbc.update("""
                UPDATE agent_run SET status='INTERRUPTED', lease_owner=NULL, lease_until=NULL,
                  error_message=?, updated_at=CURRENT_TIMESTAMP WHERE id=?
                """, message, id);
        Object historyId = rows.getFirst().get("history_id");
        if (historyId != null && jdbc.update("UPDATE chat_history SET answer=?,agent_run_key=?,update_time=CURRENT_TIMESTAMP WHERE id=?",
                message, rows.getFirst().get("run_key"), historyId) != 1) {
            throw new IllegalStateException("中断运行的回答更新失败");
        }
        return 1;
    }

    private BusinessException lost() { return new BusinessException(409, "运行执行权已失效或已被其他 Worker 占用"); }

    public final class Lease implements AutoCloseable {
        private final Fence fence;
        private final AtomicBoolean lost = new AtomicBoolean();
        private final AtomicReference<RuntimeException> stopReason = new AtomicReference<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ScheduledFuture<?> heartbeat;
        private final Runnable ownerCheckpoint;
        private Lease(Fence fence, Runnable ownerCheckpoint) {
            this.fence = fence;
            this.ownerCheckpoint = ownerCheckpoint;
            heartbeat = heartbeats.scheduleWithFixedDelay(() -> {
                if (lost.get()) return;
                try { ownerCheckpoint.run(); if (!renew(fence)) stop(lost()); }
                catch (RuntimeException failure) { stop(failure); }
            }, 15, 15, TimeUnit.SECONDS);
        }
        public Fence fence() { return fence; }
        public void requireAlive() {
            if (closed.get()) throw lost();
            if (lost.get()) throw stopReason.get();
            try { ownerCheckpoint.run(); }
            catch (RuntimeException failure) { stop(failure); throw stopReason.get(); }
        }
        private void stop(RuntimeException failure) { stopReason.compareAndSet(null, failure); lost.set(true); }
        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            heartbeat.cancel(false);
            jdbc.update("UPDATE agent_run SET lease_owner=NULL,lease_until=NULL WHERE id=? AND lease_owner=? AND execution_epoch=?",
                    fence.runId(), fence.owner(), fence.epoch());
        }
    }

    @Override @PreDestroy public void close() { heartbeats.shutdownNow(); }
}
