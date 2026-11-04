package cn.sdh.backend.job;

import cn.sdh.backend.common.context.TraceContext;
import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.service.PlatformJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class PlatformJobDispatcher {
    private final PlatformJobService jobs;
    private final ThreadPoolTaskExecutor executor;
    private final Map<String, PlatformJobHandler> handlers;
    private final String workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);

    @Value("${jobs.dispatch-batch-size:10}")
    private int batchSize;

    public PlatformJobDispatcher(PlatformJobService jobs,
                                 List<PlatformJobHandler> handlerList,
                                 @Qualifier("platformJobExecutor") ThreadPoolTaskExecutor executor) {
        this.jobs = jobs;
        this.executor = executor;
        this.handlers = new HashMap<>();
        for (PlatformJobHandler handler : handlerList) {
            PlatformJobHandler duplicate = handlers.put(handler.jobType(), handler);
            if (duplicate != null) throw new IllegalStateException("重复的任务处理器: " + handler.jobType());
        }
    }

    @Scheduled(fixedDelayString = "${jobs.dispatch-delay-ms:2000}")
    public void dispatch() {
        int freeWorkers = Math.max(0, executor.getMaxPoolSize() - executor.getActiveCount());
        if (freeWorkers == 0) return;
        int claimLimit = Math.min(Math.max(1, batchSize), freeWorkers);
        // A new lease identity for every claim batch fences late executions even on this JVM.
        String claimOwner = workerId + ":" + UUID.randomUUID();
        for (PlatformJob job : jobs.claimAvailable(claimOwner, claimLimit)) {
            try {
                executor.execute(() -> execute(job));
            } catch (RuntimeException rejected) {
                jobs.fail(job.getId(), claimOwner, rejected);
                log.warn("提交可靠任务失败: jobId={}, type={}", job.getId(), job.getJobType());
            }
        }
    }

    private void execute(PlatformJob job) {
        String workerId = job.getLeaseOwner();
        try (TraceContext.Scope ignored = TraceContext.open(job.getTraceId())) {
            PlatformJobHandler handler = handlers.get(job.getJobType());
            if (handler == null) {
                jobs.fail(job.getId(), workerId, new IllegalStateException("未注册任务处理器: " + job.getJobType()));
                return;
            }
            try {
                PlatformJobExecutionContext context = new PlatformJobExecutionContext(job.getId(), workerId, jobs);
                context.checkpoint();
                String summary = handler.execute(job, context);
                if (!jobs.complete(job.getId(), workerId, summary)) {
                    log.warn("任务完成回写失败，租约可能已丢失: jobId={}", job.getId());
                }
            } catch (PlatformJobCancelledException cancelled) {
                jobs.acknowledgeCancellation(job.getId(), workerId);
                log.info("可靠任务已取消: jobId={}, type={}", job.getId(), job.getJobType());
            } catch (Exception failure) {
                PlatformJob current = jobs.get(job.getId());
                if (current != null && PlatformJob.CANCEL_REQUESTED.equals(current.getStatus())) {
                    jobs.acknowledgeCancellation(job.getId(), workerId);
                } else {
                    jobs.fail(job.getId(), workerId, failure);
                }
                log.warn("可靠任务执行失败: jobId={}, type={}, error={}",
                        job.getId(), job.getJobType(), failure.getMessage());
            }
        }
    }
}
