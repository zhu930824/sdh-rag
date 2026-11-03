package cn.sdh.backend.job;

import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.entity.TransactionalOutbox;
import cn.sdh.backend.entity.WebhookConfig;
import cn.sdh.backend.entity.WebhookDelivery;
import cn.sdh.backend.mapper.TransactionalOutboxMapper;
import cn.sdh.backend.mapper.WebhookConfigMapper;
import cn.sdh.backend.mapper.WebhookDeliveryMapper;
import cn.sdh.backend.service.WebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookDeliveryJobHandlerTest {
    @Mock WebhookDeliveryMapper deliveryMapper;
    @Mock TransactionalOutboxMapper outboxMapper;
    @Mock WebhookConfigMapper webhookMapper;
    @Mock WebhookService webhookService;
    @Mock PlatformJobExecutionContext context;

    @Test
    void stableDeliveryIdIsPassedToReceiver() throws Exception {
        WebhookDeliveryJobHandler handler = handler();
        when(deliveryMapper.selectById(4L)).thenReturn(delivery());
        when(outboxMapper.selectById(2L)).thenReturn(outbox());
        when(webhookMapper.selectById(3L)).thenReturn(webhook((byte) 1));
        when(webhookService.triggerWebhook(any(), eq("evaluation.regression"), anyMap(), eq("delivery-4")))
                .thenReturn(true);

        handler.execute(job(1, 5), context);

        verify(deliveryMapper).markSucceeded(eq(4L), any());
    }

    @Test
    void finalFailureMarksDeliveryDeadLetter() {
        WebhookDeliveryJobHandler handler = handler();
        when(deliveryMapper.selectById(4L)).thenReturn(delivery());
        when(outboxMapper.selectById(2L)).thenReturn(outbox());
        when(webhookMapper.selectById(3L)).thenReturn(webhook((byte) 1));
        when(webhookService.triggerWebhook(any(), anyString(), anyMap(), anyString())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> handler.execute(job(5, 5), context));

        verify(deliveryMapper).markFailed(4L, WebhookDelivery.DEAD_LETTER,
                "Webhook返回失败或请求异常");
    }

    @Test
    void disabledTargetIsSkippedWithoutHttpCall() throws Exception {
        WebhookDeliveryJobHandler handler = handler();
        when(deliveryMapper.selectById(4L)).thenReturn(delivery());
        when(outboxMapper.selectById(2L)).thenReturn(outbox());
        when(webhookMapper.selectById(3L)).thenReturn(webhook((byte) 0));

        handler.execute(job(1, 5), context);

        verify(deliveryMapper).markSkipped(4L, "Webhook已删除或停用");
        verifyNoInteractions(webhookService);
    }

    private WebhookDeliveryJobHandler handler() {
        return new WebhookDeliveryJobHandler(
                deliveryMapper, outboxMapper, webhookMapper, webhookService, new ObjectMapper());
    }

    private PlatformJob job(int attempts, int maxAttempts) throws Exception {
        PlatformJob job = new PlatformJob();
        job.setPayloadJson(new ObjectMapper().writeValueAsString(new WebhookDeliveryJobHandler.Payload(4L)));
        job.setAttempts(attempts);
        job.setMaxAttempts(maxAttempts);
        return job;
    }

    private WebhookDelivery delivery() {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId(4L);
        delivery.setDeliveryKey("delivery-4");
        delivery.setOutboxId(2L);
        delivery.setWebhookId(3L);
        delivery.setStatus(WebhookDelivery.PENDING);
        return delivery;
    }

    private TransactionalOutbox outbox() {
        TransactionalOutbox outbox = new TransactionalOutbox();
        outbox.setId(2L);
        outbox.setEventType("evaluation.regression");
        outbox.setPayloadJson("{\"runId\":3}");
        return outbox;
    }

    private WebhookConfig webhook(byte status) {
        WebhookConfig webhook = new WebhookConfig();
        webhook.setId(3L);
        webhook.setName("quality-alert");
        webhook.setStatus(status);
        return webhook;
    }
}
