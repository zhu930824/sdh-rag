package cn.sdh.backend.dto;

import cn.sdh.backend.entity.PlatformJob;

import java.time.LocalDateTime;

public record PlatformJobView(
        Long id, String jobType, String resourceType, Long resourceId, String status,
        Integer priority, Integer attempts, Integer maxAttempts, LocalDateTime availableAt,
        LocalDateTime heartbeatAt, String traceId, String resultSummary, String errorMessage,
        LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime createTime, LocalDateTime updateTime) {
    public static PlatformJobView from(PlatformJob job) {
        return new PlatformJobView(job.getId(), job.getJobType(), job.getResourceType(), job.getResourceId(),
                job.getStatus(), job.getPriority(), job.getAttempts(), job.getMaxAttempts(), job.getAvailableAt(),
                job.getHeartbeatAt(), job.getTraceId(), job.getResultSummary(), job.getErrorMessage(),
                job.getStartedAt(), job.getFinishedAt(), job.getCreateTime(), job.getUpdateTime());
    }
}
