package cn.sdh.backend.service;

import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.entity.TransactionalOutbox;
import cn.sdh.backend.entity.WebhookDelivery;
import cn.sdh.backend.mapper.PlatformJobMapper;
import cn.sdh.backend.mapper.TransactionalOutboxMapper;
import cn.sdh.backend.mapper.WebhookDeliveryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReliableExecutionMetricsService {
    private final PlatformJobMapper jobMapper;
    private final TransactionalOutboxMapper outboxMapper;
    private final WebhookDeliveryMapper deliveryMapper;
    private final MeterRegistry meterRegistry;

    private final AtomicLong jobBacklogGauge = new AtomicLong();
    private final AtomicLong jobRunningGauge = new AtomicLong();
    private final AtomicLong jobDeadLetterGauge = new AtomicLong();
    private final AtomicLong outboxBacklogGauge = new AtomicLong();
    private final AtomicLong webhookBacklogGauge = new AtomicLong();
    private final AtomicLong webhookDeadLetterGauge = new AtomicLong();

    private volatile Snapshot snapshot = Snapshot.empty();
    private volatile Set<String> activeAlertCodes = Set.of();

    @Value("${operations.alerts.job-backlog:100}")
    private long jobBacklogThreshold;

    @Value("${operations.alerts.oldest-job-seconds:300}")
    private long oldestJobSecondsThreshold;

    @Value("${operations.alerts.outbox-backlog:20}")
    private long outboxBacklogThreshold;

    @Value("${operations.alerts.dead-letters-24h:1}")
    private long deadLetters24hThreshold;

    @PostConstruct
    void registerGauges() {
        register("sdh.jobs.count", "backlog", jobBacklogGauge, "等待或重试中的可靠任务数");
        register("sdh.jobs.count", "running", jobRunningGauge, "运行中的可靠任务数");
        register("sdh.jobs.count", "dead_letter", jobDeadLetterGauge, "可靠任务死信总数");
        register("sdh.outbox.count", "backlog", outboxBacklogGauge, "等待中继的Outbox事件数");
        register("sdh.webhook.deliveries.count", "backlog", webhookBacklogGauge, "等待或失败重试中的Webhook投递数");
        register("sdh.webhook.deliveries.count", "dead_letter", webhookDeadLetterGauge, "Webhook投递死信总数");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${operations.metrics.refresh-ms:15000}")
    public synchronized void refresh() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime since = now.minusHours(24);
            long pendingJobs = jobCount(new LambdaQueryWrapper<PlatformJob>()
                    .eq(PlatformJob::getStatus, PlatformJob.PENDING));
            long retryJobs = jobCount(new LambdaQueryWrapper<PlatformJob>()
                    .eq(PlatformJob::getStatus, PlatformJob.RETRY_WAIT));
            long runningJobs = jobCount(new LambdaQueryWrapper<PlatformJob>()
                    .in(PlatformJob::getStatus, PlatformJob.RUNNING, PlatformJob.CANCEL_REQUESTED));
            long deadJobs = jobCount(new LambdaQueryWrapper<PlatformJob>()
                    .eq(PlatformJob::getStatus, PlatformJob.DEAD_LETTER));
            long outboxBacklog = outboxCount(new LambdaQueryWrapper<TransactionalOutbox>()
                    .in(TransactionalOutbox::getStatus, TransactionalOutbox.NEW,
                            TransactionalOutbox.RETRY_WAIT, TransactionalOutbox.RELAYING));
            long outboxDead = outboxCount(new LambdaQueryWrapper<TransactionalOutbox>()
                    .eq(TransactionalOutbox::getStatus, TransactionalOutbox.DEAD_LETTER));
            long webhookBacklog = deliveryCount(new LambdaQueryWrapper<WebhookDelivery>()
                    .in(WebhookDelivery::getStatus, WebhookDelivery.PENDING,
                            WebhookDelivery.RUNNING, WebhookDelivery.FAILED));
            long webhookDead = deliveryCount(new LambdaQueryWrapper<WebhookDelivery>()
                    .eq(WebhookDelivery::getStatus, WebhookDelivery.DEAD_LETTER));
            long jobs24h = jobCount(new LambdaQueryWrapper<PlatformJob>()
                    .ge(PlatformJob::getCreateTime, since));
            long succeeded24h = jobCount(new LambdaQueryWrapper<PlatformJob>()
                    .eq(PlatformJob::getStatus, PlatformJob.SUCCEEDED)
                    .ge(PlatformJob::getUpdateTime, since));
            long failed24h = jobCount(new LambdaQueryWrapper<PlatformJob>()
                    .eq(PlatformJob::getStatus, PlatformJob.DEAD_LETTER)
                    .ge(PlatformJob::getUpdateTime, since));
            long retried24h = jobCount(new LambdaQueryWrapper<PlatformJob>()
                    .gt(PlatformJob::getAttempts, 1)
                    .ge(PlatformJob::getUpdateTime, since));
            long oldestQueuedSeconds = oldestQueuedSeconds(now);
            double successRate = succeeded24h + failed24h == 0 ? 100D
                    : round(100D * succeeded24h / (succeeded24h + failed24h));

            List<Alert> alerts = alerts(pendingJobs + retryJobs, oldestQueuedSeconds,
                    outboxBacklog, failed24h, outboxDead, webhookDead);
            Snapshot next = new Snapshot(now, pendingJobs, retryJobs, runningJobs, deadJobs,
                    outboxBacklog, outboxDead, webhookBacklog, webhookDead,
                    jobs24h, succeeded24h, failed24h, retried24h, successRate,
                    oldestQueuedSeconds, alerts);
            snapshot = next;
            jobBacklogGauge.set(pendingJobs + retryJobs);
            jobRunningGauge.set(runningJobs);
            jobDeadLetterGauge.set(deadJobs);
            outboxBacklogGauge.set(outboxBacklog);
            webhookBacklogGauge.set(webhookBacklog);
            webhookDeadLetterGauge.set(webhookDead);
            reportAlertTransitions(alerts);
        } catch (Exception error) {
            log.warn("可靠执行指标刷新失败: {}", error.getMessage());
        }
    }

    public Snapshot current() {
        return snapshot;
    }

    private void register(String name, String state, AtomicLong value, String description) {
        Gauge.builder(name, value, AtomicLong::get)
                .tag("state", state)
                .description(description)
                .register(meterRegistry);
    }

    private long oldestQueuedSeconds(LocalDateTime now) {
        PlatformJob oldest = jobMapper.selectOne(new LambdaQueryWrapper<PlatformJob>()
                .in(PlatformJob::getStatus, PlatformJob.PENDING, PlatformJob.RETRY_WAIT)
                .orderByAsc(PlatformJob::getCreateTime)
                .last("LIMIT 1"));
        return oldest == null || oldest.getCreateTime() == null ? 0
                : Math.max(0, Duration.between(oldest.getCreateTime(), now).getSeconds());
    }

    private List<Alert> alerts(long jobBacklog, long oldestSeconds, long outboxBacklog,
                               long failed24h, long outboxDead, long webhookDead) {
        List<Alert> alerts = new ArrayList<>();
        addIfReached(alerts, "JOB_BACKLOG", "WARNING", "可靠任务积压超过阈值",
                jobBacklog, jobBacklogThreshold);
        addIfReached(alerts, "JOB_QUEUE_AGE", "WARNING", "最老排队任务等待时间超过阈值",
                oldestSeconds, oldestJobSecondsThreshold);
        addIfReached(alerts, "OUTBOX_BACKLOG", "CRITICAL", "Outbox事件中继积压超过阈值",
                outboxBacklog, outboxBacklogThreshold);
        addIfReached(alerts, "DEAD_LETTERS_24H", "CRITICAL", "最近24小时可靠任务出现死信",
                failed24h, deadLetters24hThreshold);
        addIfReached(alerts, "OUTBOX_DEAD_LETTER", "CRITICAL", "Outbox存在死信事件",
                outboxDead, 1);
        addIfReached(alerts, "WEBHOOK_DEAD_LETTER", "WARNING", "Webhook存在投递死信",
                webhookDead, 1);
        return List.copyOf(alerts);
    }

    private void addIfReached(List<Alert> alerts, String code, String severity, String message,
                              long current, long threshold) {
        if (threshold > 0 && current >= threshold) {
            alerts.add(new Alert(code, severity, message, current, threshold));
        }
    }

    private void reportAlertTransitions(List<Alert> alerts) {
        Set<String> currentCodes = new HashSet<>();
        for (Alert alert : alerts) {
            currentCodes.add(alert.code());
            if (!activeAlertCodes.contains(alert.code())) {
                log.warn("可靠执行告警触发: code={}, current={}, threshold={}",
                        alert.code(), alert.current(), alert.threshold());
            }
        }
        for (String previous : activeAlertCodes) {
            if (!currentCodes.contains(previous)) log.info("可靠执行告警恢复: code={}", previous);
        }
        activeAlertCodes = Set.copyOf(currentCodes);
    }

    private long jobCount(LambdaQueryWrapper<PlatformJob> query) {
        return jobMapper.selectCount(query);
    }

    private long outboxCount(LambdaQueryWrapper<TransactionalOutbox> query) {
        return outboxMapper.selectCount(query);
    }

    private long deliveryCount(LambdaQueryWrapper<WebhookDelivery> query) {
        return deliveryMapper.selectCount(query);
    }

    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

    public record Alert(String code, String severity, String message, long current, long threshold) {}

    public record Snapshot(LocalDateTime sampledAt,
                           long pendingJobs, long retryWaitJobs, long runningJobs, long deadLetterJobs,
                           long outboxBacklog, long outboxDeadLetters,
                           long webhookBacklog, long webhookDeadLetters,
                           long jobs24h, long succeeded24h, long failed24h, long retried24h,
                           double successRate24h, long oldestQueuedSeconds, List<Alert> alerts) {
        static Snapshot empty() {
            return new Snapshot(null, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 100D, 0, List.of());
        }
    }
}
