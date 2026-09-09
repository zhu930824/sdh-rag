package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.mapper.PlatformJobMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformJobService {
    private final PlatformJobMapper mapper;

    @Value("${jobs.lease-seconds:600}")
    private int leaseSeconds;

    public record EnqueueCommand(String jobKey, String jobType, String resourceType, Long resourceId,
                                 String payloadJson, Integer priority, Integer maxAttempts, Long createdBy) {}

    public PlatformJob enqueue(EnqueueCommand command) {
        if (command == null || blank(command.jobKey()) || blank(command.jobType()) || command.createdBy() == null) {
            throw new IllegalArgumentException("任务幂等键、类型和创建人不能为空");
        }
        if (command.jobKey().length() > 160 || command.jobType().length() > 60
                || (command.resourceType() != null && command.resourceType().length() > 40)) {
            throw new IllegalArgumentException("任务键或类型超过长度限制");
        }
        PlatformJob existing = findByKey(command.jobKey());
        if (existing != null) return existing;

        LocalDateTime now = LocalDateTime.now();
        PlatformJob job = new PlatformJob();
        job.setJobKey(command.jobKey());
        job.setJobType(command.jobType());
        job.setResourceType(command.resourceType());
        job.setResourceId(command.resourceId());
        job.setPayloadJson(command.payloadJson());
        job.setStatus(PlatformJob.PENDING);
        job.setPriority(command.priority() == null ? 0 : command.priority());
        job.setAttempts(0);
        job.setMaxAttempts(Math.max(1, Math.min(command.maxAttempts() == null ? 3 : command.maxAttempts(), 10)));
        job.setAvailableAt(now);
        job.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        job.setCreatedBy(command.createdBy());
        job.setCreateTime(now);
        job.setUpdateTime(now);
        try {
            mapper.insert(job);
            return job;
        } catch (DuplicateKeyException ignored) {
            return findByKey(command.jobKey());
        }
    }

    public List<PlatformJob> claimAvailable(String workerId, int limit) {
        LocalDateTime now = LocalDateTime.now();
        mapper.cancelExpiredRequested(now);
        mapper.deadLetterExpired(now);
        List<PlatformJob> claimed = new ArrayList<>();
        for (Long id : mapper.selectClaimCandidates(now, Math.max(1, Math.min(limit, 100)))) {
            if (mapper.claim(id, workerId, now, now.plusSeconds(Math.max(30, leaseSeconds))) == 1) {
                claimed.add(mapper.selectById(id));
            }
        }
        return claimed;
    }

    public boolean heartbeat(Long jobId, String workerId) {
        LocalDateTime now = LocalDateTime.now();
        return mapper.heartbeat(jobId, workerId, now, now.plusSeconds(Math.max(30, leaseSeconds))) == 1;
    }

    public boolean complete(Long jobId, String workerId, String summary) {
        return mapper.complete(jobId, workerId, cap(summary, 1000), LocalDateTime.now()) == 1;
    }

    public boolean fail(Long jobId, String workerId, Throwable failure) {
        PlatformJob job = mapper.selectById(jobId);
        if (job == null) return false;
        LocalDateTime now = LocalDateTime.now();
        boolean exhausted = job.getAttempts() != null && job.getMaxAttempts() != null
                && job.getAttempts() >= job.getMaxAttempts();
        String status = exhausted ? PlatformJob.DEAD_LETTER : PlatformJob.RETRY_WAIT;
        long delay = Math.min(1800, 10L * (1L << Math.min(Math.max(0, job.getAttempts() - 1), 7)));
        return mapper.fail(jobId, workerId, status, safeError(failure), now.plusSeconds(delay),
                exhausted ? now : null, now) == 1;
    }

    public boolean acknowledgeCancellation(Long jobId, String workerId) {
        return mapper.acknowledgeCancellation(jobId, workerId, LocalDateTime.now()) == 1;
    }

    public PlatformJob get(Long id) {
        return mapper.selectById(id);
    }

    public PlatformJob requireOwned(Long id, Long actor) {
        PlatformJob job = mapper.selectById(id);
        if (job == null) throw new BusinessException(404, "任务不存在");
        if (actor == null) throw new BusinessException(401, "请先登录");
        if (!actor.equals(job.getCreatedBy())) throw new BusinessException(403, "只能访问自己创建的任务");
        return job;
    }

    public IPage<PlatformJob> listMine(Long actor, String status, String jobType, int page, int pageSize) {
        if (actor == null) throw new BusinessException(401, "请先登录");
        LambdaQueryWrapper<PlatformJob> query = new LambdaQueryWrapper<PlatformJob>()
                .eq(PlatformJob::getCreatedBy, actor)
                .eq(!blank(status), PlatformJob::getStatus, status)
                .eq(!blank(jobType), PlatformJob::getJobType, jobType)
                .orderByDesc(PlatformJob::getCreateTime);
        return mapper.selectPage(new Page<>(Math.max(1, page), Math.max(1, Math.min(pageSize, 100))), query);
    }

    public void cancel(Long id, Long actor) {
        PlatformJob job = requireOwned(id, actor);
        if (List.of(PlatformJob.SUCCEEDED, PlatformJob.CANCELLED).contains(job.getStatus())) {
            throw new BusinessException(409, "当前任务状态不可取消");
        }
        LocalDateTime now = LocalDateTime.now();
        if (mapper.cancelWaiting(id, now) == 0 && mapper.requestRunningCancellation(id, now) == 0) {
            throw new BusinessException(409, "任务状态已变化，请刷新后重试");
        }
    }

    public void replay(Long id, Long actor) {
        requireOwned(id, actor);
        if (mapper.replay(id, LocalDateTime.now()) == 0) {
            throw new BusinessException(409, "只有失败、死信或已取消的任务可以重放");
        }
    }

    private PlatformJob findByKey(String key) {
        return mapper.selectOne(new LambdaQueryWrapper<PlatformJob>().eq(PlatformJob::getJobKey, key));
    }

    private String safeError(Throwable failure) {
        if (failure == null) return "未知错误";
        String value = failure.getMessage();
        if (blank(value)) value = failure.getClass().getSimpleName();
        return cap(value, 2000);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String cap(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(value.length(), max));
    }
}
