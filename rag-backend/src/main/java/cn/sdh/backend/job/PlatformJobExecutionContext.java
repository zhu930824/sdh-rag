package cn.sdh.backend.job;

import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.service.PlatformJobService;

public final class PlatformJobExecutionContext {
    private final Long jobId;
    private final String workerId;
    private final PlatformJobService jobs;

    public PlatformJobExecutionContext(Long jobId, String workerId, PlatformJobService jobs) {
        this.jobId = jobId;
        this.workerId = workerId;
        this.jobs = jobs;
    }

    public Long jobId() { return jobId; }

    /** Refreshes the lease and provides a cooperative cancellation checkpoint. */
    public void checkpoint() {
        PlatformJob current = jobs.get(jobId);
        if (current == null || PlatformJob.CANCEL_REQUESTED.equals(current.getStatus())
                || PlatformJob.CANCELLED.equals(current.getStatus())) {
            throw new PlatformJobCancelledException();
        }
        if (!jobs.heartbeat(jobId, workerId)) {
            throw new IllegalStateException("任务租约已丢失，停止当前执行");
        }
    }
}
