package cn.sdh.backend.service;

import cn.sdh.backend.common.context.TraceContext;
import cn.sdh.backend.entity.TransactionalOutbox;
import cn.sdh.backend.entity.WebhookConfig;
import cn.sdh.backend.entity.WebhookDelivery;
import cn.sdh.backend.mapper.TransactionalOutboxMapper;
import cn.sdh.backend.mapper.WebhookDeliveryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionalOutboxServiceTest {
    @Mock TransactionalOutboxMapper outboxMapper;
    @Mock WebhookDeliveryMapper deliveryMapper;

    @Test
    void eventAndRecipientSnapshotsAreCreatedTogether() {
        TransactionalOutboxService service = new TransactionalOutboxService(
                outboxMapper, deliveryMapper, new ObjectMapper());
        when(outboxMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            TransactionalOutbox event = invocation.getArgument(0);
            event.setId(21L);
            return 1;
        }).when(outboxMapper).insert(any(TransactionalOutbox.class));
        WebhookConfig webhook = new WebhookConfig();
        webhook.setId(7L);

        String traceId = "fedcba9876543210fedcba9876543210";
        TransactionalOutbox event;
        try (TraceContext.Scope ignored = TraceContext.open(traceId)) {
            event = service.publishWebhook(
                    "webhook:evaluation.regression:3", "evaluation.regression",
                    Map.of("runId", 3L), List.of(webhook), 9L);
        }

        assertEquals(21L, event.getId());
        assertEquals(TransactionalOutbox.NEW, event.getStatus());
        assertEquals(9L, event.getCreatedBy());
        assertEquals(traceId, event.getTraceId());
        ArgumentCaptor<WebhookDelivery> delivery = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryMapper).insert(delivery.capture());
        assertEquals(21L, delivery.getValue().getOutboxId());
        assertEquals(7L, delivery.getValue().getWebhookId());
        assertEquals(WebhookDelivery.PENDING, delivery.getValue().getStatus());
        assertNotNull(delivery.getValue().getDeliveryKey());
    }

    @Test
    void duplicateEventKeyDoesNotCreateMoreDeliveries() {
        TransactionalOutboxService service = new TransactionalOutboxService(
                outboxMapper, deliveryMapper, new ObjectMapper());
        TransactionalOutbox existing = new TransactionalOutbox();
        existing.setId(22L);
        when(outboxMapper.selectOne(any())).thenReturn(existing);

        TransactionalOutbox result = service.publishWebhook(
                "webhook:same", "evaluation.regression", Map.of(), List.of(), 9L);

        assertSame(existing, result);
        verify(outboxMapper, never()).insert(any(TransactionalOutbox.class));
        verify(deliveryMapper, never()).insert(any(WebhookDelivery.class));
    }
}
