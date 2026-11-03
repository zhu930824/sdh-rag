package cn.sdh.backend.job;

import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.entity.TransactionalOutbox;
import cn.sdh.backend.entity.WebhookConfig;
import cn.sdh.backend.entity.WebhookDelivery;
import cn.sdh.backend.mapper.TransactionalOutboxMapper;
import cn.sdh.backend.mapper.WebhookConfigMapper;
import cn.sdh.backend.mapper.WebhookDeliveryMapper;
import cn.sdh.backend.service.WebhookService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebhookDeliveryJobHandler implements PlatformJobHandler {
    public static final String TYPE = "WEBHOOK_DELIVERY";

    private final WebhookDeliveryMapper deliveryMapper;
    private final TransactionalOutboxMapper outboxMapper;
    private final WebhookConfigMapper webhookMapper;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    public record Payload(Long deliveryId) {}

    @Override
    public String jobType() {
        return TYPE;
    }

    @Override
    public String execute(PlatformJob job, PlatformJobExecutionContext context) throws Exception {
        Payload payload = objectMapper.readValue(job.getPayloadJson(), Payload.class);
        if (payload.deliveryId() == null) throw new IllegalArgumentException("Webhook投递 ID 不能为空");
        WebhookDelivery delivery = deliveryMapper.selectById(payload.deliveryId());
        if (delivery == null) throw new IllegalStateException("Webhook投递记录不存在");
        if (WebhookDelivery.SUCCEEDED.equals(delivery.getStatus())
                || WebhookDelivery.SKIPPED.equals(delivery.getStatus())) {
            return "Webhook投递已完成，幂等跳过";
        }

        TransactionalOutbox outbox = outboxMapper.selectById(delivery.getOutboxId());
        if (outbox == null) throw new IllegalStateException("Outbox事件不存在");
        WebhookConfig webhook = webhookMapper.selectById(delivery.getWebhookId());
        if (webhook == null || webhook.getStatus() == null || webhook.getStatus() != 1) {
            deliveryMapper.markSkipped(delivery.getId(), "Webhook已删除或停用");
            return "Webhook已删除或停用，投递已跳过";
        }

        context.checkpoint();
        int attempts = job.getAttempts() == null ? 1 : job.getAttempts();
        deliveryMapper.markRunning(delivery.getId(), attempts);
        Map<String, Object> eventPayload = objectMapper.readValue(
                outbox.getPayloadJson(), new TypeReference<Map<String, Object>>() {});
        boolean delivered = webhookService.triggerWebhook(
                webhook, outbox.getEventType(), eventPayload, delivery.getDeliveryKey());
        if (delivered) {
            deliveryMapper.markSucceeded(delivery.getId(), LocalDateTime.now());
            return "Webhook投递成功: " + webhook.getName();
        }

        boolean exhausted = attempts >= (job.getMaxAttempts() == null ? 5 : job.getMaxAttempts());
        String error = "Webhook返回失败或请求异常";
        deliveryMapper.markFailed(delivery.getId(),
                exhausted ? WebhookDelivery.DEAD_LETTER : WebhookDelivery.FAILED, error);
        throw new IllegalStateException(error);
    }
}
