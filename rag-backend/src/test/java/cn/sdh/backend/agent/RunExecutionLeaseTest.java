package cn.sdh.backend.agent;

import cn.sdh.backend.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RunExecutionLeaseTest {
    JdbcTemplate jdbc;
    RunExecutionLeaseService leases;

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE agent_run(id BIGINT PRIMARY KEY, run_key VARCHAR(64) UNIQUE, user_id BIGINT, status VARCHAR(30), updated_at TIMESTAMP, history_id BIGINT, error_message VARCHAR(2000))");
        jdbc.execute("CREATE TABLE chat_history(id BIGINT PRIMARY KEY, answer LONGTEXT, update_time TIMESTAMP, agent_run_key VARCHAR(64))");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V33__agent_run_execution_lease.sql")).execute(source);
        jdbc.update("INSERT INTO chat_history(id,answer) VALUES(10,'旧回答')");
        jdbc.update("INSERT INTO agent_run(id,run_key,user_id,status,updated_at,history_id) VALUES(1,'run-1',7,'RUNNING',CURRENT_TIMESTAMP,10)");
        leases = new RunExecutionLeaseService(jdbc, new DataSourceTransactionManager(source));
    }
    @AfterEach void close() { leases.close(); }

    @Test void onlyOneWorkerCanClaimAndAnotherUserCannotClaim() {
        assertThrows(BusinessException.class, () -> leases.claim("run-1", 8L));
        try (var lease = leases.claim("run-1", 7L)) {
            assertThrows(BusinessException.class, () -> leases.claim("run-1", 7L));
            assertEquals(1L, lease.fence().epoch());
            assertEquals("ok", leases.guarded(lease.fence(), () -> "ok"));
        }
    }

    @Test void twoSimultaneousSubscribersStillHaveExactlyOneOwner() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        var claimed = new java.util.concurrent.CopyOnWriteArrayList<RunExecutionLeaseService.Lease>();
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                start.await();
                try { claimed.add(leases.claim("run-1", 7L)); return true; }
                catch (BusinessException expected) { return false; }
            };
            var first = workers.submit(attempt); var second = workers.submit(attempt); start.countDown();
            int owners = (first.get(5, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(5, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, owners);
            assertEquals(0, leases.interruptExpired());
        } finally { claimed.forEach(RunExecutionLeaseService.Lease::close); }
    }

    @Test void expiredWorkerCannotWriteEvenBeforeTheScannerRuns() {
        try (var lease = leases.claim("run-1", 7L)) {
            expire();
            assertThrows(BusinessException.class, () -> leases.guarded(lease.fence(), () -> {
                jdbc.update("UPDATE chat_history SET answer='过期写入' WHERE id=10"); return true;
            }));
            assertEquals("旧回答", answer());
        }
    }

    @Test void aLostOwningJobStopsTheRunAtItsNextExecutionCheckpoint() {
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable owner = () -> { if (cancelled.get()) throw new IllegalStateException("job cancelled"); };
        try (var lease = leases.claim("run-1", 7L, owner)) {
            lease.requireAlive();
            cancelled.set(true);
            assertThrows(IllegalStateException.class, lease::requireAlive);
            cancelled.set(false);
            // A transient return of the owning job must not revive a locally fenced execution.
            assertThrows(IllegalStateException.class, lease::requireAlive);
        }
    }

    @Test void cancellationReasonMustSurviveSubsequentExecutionCheckpoints() {
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        try (var lease = leases.claim("run-1", 7L, () -> {
            if (cancelled.get()) throw new cn.sdh.backend.job.PlatformJobCancelledException();
        })) {
            cancelled.set(true);
            assertThrows(cn.sdh.backend.job.PlatformJobCancelledException.class, lease::requireAlive);
            assertThrows(cn.sdh.backend.job.PlatformJobCancelledException.class, lease::requireAlive);
        }
    }

    @Test void interruptionIsAtomicAndOldWorkerCannotRenewOrOverwriteNewAttempt() {
        try (var old = leases.claim("run-1", 7L)) {
            expire();
            assertEquals(1, leases.interruptExpired());
            assertEquals("INTERRUPTED", jdbc.queryForObject("SELECT status FROM agent_run WHERE id=1", String.class));
            assertTrue(answer().contains("中断"));
            assertEquals("run-1", jdbc.queryForObject("SELECT agent_run_key FROM chat_history WHERE id=10", String.class));
            // Simulate a future explicitly authorized recovery. Closing the old lease must not release the new one.
            jdbc.update("UPDATE agent_run SET status='RUNNING' WHERE id=1");
            try (var next = leases.claim("run-1", 7L)) {
                assertEquals(2, next.fence().epoch());
                assertFalse(leases.renew(old.fence()));
                assertThrows(BusinessException.class, () -> leases.guarded(old.fence(), () -> "late"));
                old.close();
                assertEquals("new", leases.guarded(next.fence(), () -> "new"));
            }
        }
    }

    @Test void aDatabaseFailureRollsBackTheGuardedBusinessChange() {
        try (var lease = leases.claim("run-1", 7L)) {
            assertThrows(IllegalStateException.class, () -> leases.guarded(lease.fence(), () -> {
                jdbc.update("UPDATE chat_history SET answer='未提交' WHERE id=10");
                throw new IllegalStateException("rollback");
            }));
            assertEquals("旧回答", answer());
        }
    }

    @Test void staleSdkCheckpointCannotOverwriteOrDeleteTheLastGoodState() {
        jdbc.execute("CREATE TABLE agent_runtime_state(user_id VARCHAR(64),session_id VARCHAR(100),state_key VARCHAR(100),state_version BIGINT,payload LONGTEXT,updated_at TIMESTAMP,PRIMARY KEY(user_id,session_id,state_key))");
        var store = new cn.sdh.backend.agent.runtime.JdbcAgentStateStore(jdbc, leases);
        var state = io.agentscope.core.state.AgentState.builder().userId("7").sessionId("run-1").build();
        try (var lease = leases.claim("run-1", 7L)) {
            var scoped = store.fenced(lease.fence());
            assertEquals(1, scoped.saveIfVersion("7", "run-1", "agent_state", state, 0));
            assertThrows(BusinessException.class, () -> scoped.saveIfVersion("8", "run-1", "agent_state", state, 0));
            expire();
            assertThrows(BusinessException.class, () -> scoped.saveIfVersion("7", "run-1", "agent_state", state, 1));
            assertThrows(BusinessException.class, () -> scoped.delete("7", "run-1"));
            assertEquals(1, store.getVersioned("7", "run-1", "agent_state", io.agentscope.core.state.AgentState.class).version());
        }
    }

    @Test void interruptionRollsBackIfTheVisibleAnswerCannotBeUpdated() {
        try (var lease = leases.claim("run-1", 7L)) {
            jdbc.update("DELETE FROM chat_history WHERE id=10");
            expire();
            assertThrows(IllegalStateException.class, leases::interruptExpired);
            assertEquals("RUNNING", jdbc.queryForObject("SELECT status FROM agent_run WHERE id=1", String.class));
            assertNotNull(jdbc.queryForObject("SELECT lease_owner FROM agent_run WHERE id=1", String.class));
        }
    }

    private void expire() { jdbc.update("UPDATE agent_run SET lease_until=DATEADD('SECOND',-10,CURRENT_TIMESTAMP) WHERE id=1"); }
    private String answer() { return jdbc.queryForObject("SELECT answer FROM chat_history WHERE id=10", String.class); }
}
