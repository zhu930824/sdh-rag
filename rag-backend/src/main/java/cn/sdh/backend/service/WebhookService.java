package cn.sdh.backend.service;

import cn.sdh.backend.entity.WebhookConfig;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface WebhookService extends IService<WebhookConfig> {

    IPage<WebhookConfig> getPage(Integer page, Integer pageSize, Integer status);

    List<WebhookConfig> getActiveWebhooks();

    List<WebhookConfig> getWebhooksByEvent(String event);

    WebhookConfig createWebhook(WebhookConfig webhook);

    void updateWebhook(WebhookConfig webhook);

    void toggleStatus(Long webhookId);

    void deleteWebhook(Long webhookId);

    void triggerWebhooks(String event, Map<String, Object> payload);

    boolean triggerWebhook(WebhookConfig webhook, String event, Map<String, Object> payload);

    void testWebhook(Long webhookId);
}
