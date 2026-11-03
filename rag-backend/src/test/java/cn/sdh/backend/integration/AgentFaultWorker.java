package cn.sdh.backend.integration;

import cn.sdh.backend.agent.RunExecutionLeaseService;
import cn.sdh.backend.service.AgentActionExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** A real disposable JVM process. The parent kills it at an observed durable boundary. */
public final class AgentFaultWorker {
    public static void main(String[] arguments) throws Exception {
        var env = System.getenv();
        var source = new DriverManagerDataSource(env.get("SDH_IT_CHILD_URL"), "root", env.get("SDH_IT_CHILD_PASSWORD"));
        try (var context = MysqlAgentContext.open(source)) {
            String mode = env.get("SDH_IT_CHILD_MODE");
            if ("action".equals(mode)) {
                context.getBean(AgentActionExecutor.class).execute(Long.valueOf(env.get("SDH_IT_CHILD_ACTION")), 1);
                return;
            }
            try (var lease = context.getBean(RunExecutionLeaseService.class).claim(env.get("SDH_IT_CHILD_RUN"), 1L)) {
                if ("transaction".equals(mode)) {
                    context.getBean(RunExecutionLeaseService.class).guarded(lease.fence(), () -> {
                        context.getBean(JdbcTemplate.class).update("UPDATE chat_history SET answer='uncommitted' WHERE id=?",
                                Long.valueOf(env.get("SDH_IT_CHILD_HISTORY")));
                        signal("TRANSACTION_READY"); waitForTermination(); return true;
                    });
                } else {
                    signal("LEASE_READY"); waitForTermination();
                }
            }
        }
    }
    private static void signal(String value) { System.out.println(value); System.out.flush(); }
    private static void waitForTermination() {
        try { new java.util.concurrent.CountDownLatch(1).await(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
    }
}
