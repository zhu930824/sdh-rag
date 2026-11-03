package cn.sdh.backend.service;

import cn.sdh.backend.entity.TransactionalOutbox;
import cn.sdh.backend.entity.WebhookDelivery;
import cn.sdh.backend.job.WebhookDeliveryJobHandler;
import cn.sdh.backend.mapper.TransactionalOutboxMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionalOutboxRelay {
    private final TransactionalOutboxMapper mapper;
    private final TransactionalOutboxService outboxService;
    private final PlatformJobService jobs;
    private final ObjectMapper objectMapper;

    private final String workerId = "outbox-" + UUID.randomUUID().toString().substring(0, 8);

    @Value("${outbox.relay.batch-size:50}")
    private int batchSize;

    @Value("${outbox.relay.lease-seconds:60}")
    private int leaseSeconds;

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:1000}")
    public void relay() {
        LocalDateTime now = LocalDateTime.now();
        mapper.deadLetterExpired(now);
        List<Long> candidates = mapper.selectRelayCandidates(now, Math.max(1, Math.min(batchSize, 200)));
        for (Long id : candidates) {
            if (mapper.claim(id, workerId, now, now.plusSeconds(Math.max(30, leaseSeconds))) != 1) continue;
            relayOne(id);
        }
    }

    void relayOne(Long id) {
        TransactionalOutbox outbox = mapper.selectById(id);
        if (outbox == null) return;
        try {
            for (WebhookDelivery delivery : outboxService.listDeliveries(id)) {
                jobs.enqueue(new PlatformJobService.EnqueueCommand(
                        "webhook-delivery:" + delivery.getDeliveryKey(),
                        WebhookDeliveryJobHandler.TYPE,
                        "WEBHOOK_DELIVERY",
                        delivery.getId(),
                        payload(delivery.getId()),
                        5,
                        5,
                        outbox.getCreatedBy(),
                        outbox.getTraceId()));
            }
            mapper.markPublished(id, workerId, LocalDateTime.now());
        } catch (Exception failure) {
            int attempts = outbox.getRelayAttempts() == null ? 1 : outbox.getRelayAttempts();
            boolean exhausted = attempts >= (outbox.getMaxAttempts() == null ? 10 : outbox.getMaxAttempts());
            long delay = Math.min(1800, 5L * (1L << Math.min(Math.max(0, attempts - 1), 8)));
            mapper.markFailed(id, workerId,
                    exhausted ? TransactionalOutbox.DEAD_LETTER : TransactionalOutbox.RETRY_WAIT,
                    LocalDateTime.now().plusSeconds(delay), cap(failure.getMessage(), 2000));
            log.error("Outbox事件中继失败: id={}, attempts={}", id, attempts, failure);
        }
    }

    private String payload(Long deliveryId) throws JsonProcessingException {
        return objectMapper.writeValueAsString(new WebhookDeliveryJobHandler.Payload(deliveryId));
    }

    private String cap(String value, int max) {
        if (value == null || value.isBlank()) return "未知中继错误";
        return value.substring(0, Math.min(value.length(), max));
    }
}
