package cn.sdh.backend.service;

import cn.sdh.backend.common.context.TraceContext;
import cn.sdh.backend.entity.TransactionalOutbox;
import cn.sdh.backend.entity.WebhookConfig;
import cn.sdh.backend.entity.WebhookDelivery;
import cn.sdh.backend.mapper.TransactionalOutboxMapper;
import cn.sdh.backend.mapper.WebhookDeliveryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionalOutboxService {
    private final TransactionalOutboxMapper outboxMapper;
    private final WebhookDeliveryMapper deliveryMapper;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public TransactionalOutbox publishWebhook(String eventKey, String eventType,
                                               Map<String, Object> payload,
                                               List<WebhookConfig> recipients,
                                               Long createdBy) {
        if (blank(eventKey) || blank(eventType)) {
            throw new IllegalArgumentException("事件幂等键和类型不能为空");
        }
        if (eventKey.length() > 160 || eventType.length() > 128) {
            throw new IllegalArgumentException("事件幂等键或类型超过长度限制");
        }
        TransactionalOutbox existing = findByEventKey(eventKey);
        if (existing != null) return existing;

        LocalDateTime now = LocalDateTime.now();
        TransactionalOutbox outbox = new TransactionalOutbox();
        outbox.setEventKey(eventKey);
        outbox.setEventType(eventType);
        outbox.setPayloadJson(writePayload(payload));
        outbox.setStatus(TransactionalOutbox.NEW);
        outbox.setRelayAttempts(0);
        outbox.setMaxAttempts(10);
        outbox.setAvailableAt(now);
        outbox.setTraceId(TraceContext.currentOrCreate());
        outbox.setCreatedBy(createdBy == null ? 0L : createdBy);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        try {
            outboxMapper.insert(outbox);
        } catch (DuplicateKeyException ignored) {
            return findByEventKey(eventKey);
        }

        for (WebhookConfig recipient : recipients == null ? List.<WebhookConfig>of() : recipients) {
            WebhookDelivery delivery = new WebhookDelivery();
            delivery.setDeliveryKey(UUID.randomUUID().toString().replace("-", ""));
            delivery.setOutboxId(outbox.getId());
            delivery.setWebhookId(recipient.getId());
            delivery.setEventType(eventType);
            delivery.setStatus(WebhookDelivery.PENDING);
            delivery.setAttempts(0);
            delivery.setCreateTime(now);
            delivery.setUpdateTime(now);
            deliveryMapper.insert(delivery);
        }
        return outbox;
    }

    public TransactionalOutbox get(Long id) {
        return outboxMapper.selectById(id);
    }

    public List<WebhookDelivery> listDeliveries(Long outboxId) {
        return deliveryMapper.selectList(new LambdaQueryWrapper<WebhookDelivery>()
                .eq(WebhookDelivery::getOutboxId, outboxId)
                .orderByAsc(WebhookDelivery::getId));
    }

    private TransactionalOutbox findByEventKey(String eventKey) {
        return outboxMapper.selectOne(new LambdaQueryWrapper<TransactionalOutbox>()
                .eq(TransactionalOutbox::getEventKey, eventKey));
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Webhook事件载荷无法序列化", e);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
