package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.WebhookConfig;
import cn.sdh.backend.mapper.WebhookConfigMapper;
import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.context.TraceContext;
import cn.sdh.backend.service.TransactionalOutboxService;
import cn.sdh.backend.service.WebhookService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl extends ServiceImpl<WebhookConfigMapper, WebhookConfig> implements WebhookService {

    private final WebhookConfigMapper webhookConfigMapper;
    private final ObjectMapper objectMapper;
    private final TransactionalOutboxService outboxService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${security.webhook.allow-http:false}")
    private boolean allowHttp;

    @Value("${security.webhook.allow-private-network:false}")
    private boolean allowPrivateNetwork;

    @PostConstruct
    void configureHttpClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);
        restTemplate.setRequestFactory(requestFactory);
    }

    @Override
    public IPage<WebhookConfig> getPage(Integer page, Integer pageSize, Integer status) {
        Page<WebhookConfig> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<WebhookConfig> wrapper = new LambdaQueryWrapper<>();
        
        if (status != null) {
            wrapper.eq(WebhookConfig::getStatus, status);
        }
        wrapper.orderByDesc(WebhookConfig::getCreateTime);
        
        return page(pageParam, wrapper);
    }

    @Override
    public List<WebhookConfig> getActiveWebhooks() {
        return webhookConfigMapper.selectActiveWebhooks();
    }

    @Override
    public List<WebhookConfig> getWebhooksByEvent(String event) {
        return webhookConfigMapper.selectByEvent(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WebhookConfig createWebhook(WebhookConfig webhook) {
        validateWebhook(webhook);
        
        webhook.setStatus((byte) 1);
        webhook.setFailCount(0);
        webhook.setCreateTime(LocalDateTime.now());
        webhook.setUpdateTime(LocalDateTime.now());
        save(webhook);
        
        return webhook;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWebhook(WebhookConfig webhook) {
        WebhookConfig existing = getById(webhook.getId());
        if (existing == null) {
            throw new RuntimeException("Webhook不存在");
        }

        if (webhook.getSecret() == null || webhook.getSecret().isBlank()) {
            webhook.setSecret(existing.getSecret());
        }
        if (webhook.getHeaders() == null || webhook.getHeaders().isBlank()) {
            webhook.setHeaders(existing.getHeaders());
        }
        validateWebhook(webhook);
        
        webhook.setUpdateTime(LocalDateTime.now());
        updateById(webhook);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long webhookId) {
        WebhookConfig webhook = getById(webhookId);
        if (webhook == null) {
            throw new RuntimeException("Webhook不存在");
        }

        webhook.setStatus((byte) (webhook.getStatus() == 1 ? 0 : 1));
        webhook.setUpdateTime(LocalDateTime.now());
        updateById(webhook);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWebhook(Long webhookId) {
        removeById(webhookId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void triggerWebhooks(String event, Map<String, Object> payload) {
        triggerWebhooks(event, payload, UUID.randomUUID().toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void triggerWebhooks(String event, Map<String, Object> payload, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Webhook事件幂等键不能为空");
        }
        String eventKey = idempotencyKey.startsWith("webhook:")
                ? idempotencyKey : "webhook:" + idempotencyKey;
        outboxService.publishWebhook(eventKey, event, payload, getWebhooksByEvent(event),
                UserContext.getCurrentUserId());
    }

    @Override
    public boolean triggerWebhook(WebhookConfig webhook, String event, Map<String, Object> payload) {
        return triggerWebhook(webhook, event, payload, null);
    }

    @Override
    public boolean triggerWebhook(WebhookConfig webhook, String event, Map<String, Object> payload, String deliveryId) {
        log.info("触发Webhook: {} (Event: {})", webhook.getName(), event);

        try {
            validateNetworkTarget(webhook.getUrl());
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("event", event);
            requestBody.put("timestamp", System.currentTimeMillis());
            requestBody.put("data", payload);
            if (deliveryId != null && !deliveryId.isBlank()) requestBody.put("deliveryId", deliveryId);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (webhook.getHeaders() != null && !webhook.getHeaders().isEmpty()) {
                try {
                    Map<String, String> customHeaders = objectMapper.readValue(
                        webhook.getHeaders(), 
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class)
                    );
                    customHeaders.forEach(headers::set);
                } catch (JsonProcessingException e) {
                    log.warn("解析自定义请求头失败: {}", e.getMessage());
                }
            }

            if (deliveryId != null && !deliveryId.isBlank()) {
                headers.set("X-Webhook-Delivery", deliveryId);
            }
            if (TraceContext.current() != null) {
                headers.set("X-Trace-Id", TraceContext.current());
                headers.set("traceparent", "00-" + TraceContext.current() + "-"
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "-01");
            }

            if (webhook.getSecret() != null && !webhook.getSecret().isEmpty()) {
                String signature = generateSignature(jsonBody, webhook.getSecret());
                headers.set("X-Webhook-Signature", signature);
            }

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(
                webhook.getUrl(),
                HttpMethod.POST,
                entity,
                String.class
            );
            long duration = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful()) {
                webhookConfigMapper.updateTriggerTime(webhook.getId());
                webhookConfigMapper.resetFailCount(webhook.getId());
                log.info("Webhook触发成功: {} (耗时: {}ms)", webhook.getName(), duration);
                return true;
            } else {
                webhookConfigMapper.incrementFailCount(webhook.getId());
                log.warn("Webhook返回非2xx状态: {} - {}", webhook.getName(), response.getStatusCode());
                return false;
            }
        } catch (RestClientException e) {
            webhookConfigMapper.incrementFailCount(webhook.getId());
            log.error("Webhook请求失败: {} - {}", webhook.getName(), e.getMessage());
            return false;
        } catch (Exception e) {
            webhookConfigMapper.incrementFailCount(webhook.getId());
            log.error("Webhook处理异常: {} - {}", webhook.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public void testWebhook(Long webhookId) {
        WebhookConfig webhook = getById(webhookId);
        if (webhook == null) {
            throw new RuntimeException("Webhook不存在");
        }

        Map<String, Object> testPayload = new HashMap<>();
        testPayload.put("test", true);
        testPayload.put("message", "这是一条测试消息");

        boolean success = triggerWebhook(webhook, "test", testPayload);
        if (success) {
            log.info("Webhook测试成功: {}", webhook.getName());
        } else {
            throw new RuntimeException("Webhook测试失败");
        }
    }

    private void validateWebhook(WebhookConfig webhook) {
        if (webhook.getUrl() == null || webhook.getUrl().isBlank()) {
            throw new RuntimeException("URL不能为空");
        }

        validateNetworkTarget(webhook.getUrl());

        if (webhook.getEvents() == null || webhook.getEvents().isEmpty()) {
            throw new RuntimeException("请至少选择一个订阅事件");
        }
    }

    private void validateNetworkTarget(String value) {
        try {
            URI uri = URI.create(value.trim());
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            boolean explicitlyAllowedHttp = allowHttp && "http".equalsIgnoreCase(uri.getScheme());
            if (!https && !explicitlyAllowedHttp) {
                throw new RuntimeException("Webhook 必须使用 HTTPS；测试 HTTP 需显式启用");
            }
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new RuntimeException("Webhook URL格式不正确");
            }
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isMulticastAddress()) {
                    throw new RuntimeException("Webhook 禁止访问回环、链路本地或组播地址");
                }
                byte[] raw = address.getAddress();
                boolean uniqueLocalV6 = raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
                if (!allowPrivateNetwork && (address.isSiteLocalAddress() || uniqueLocalV6)) {
                    throw new RuntimeException("Webhook 默认禁止访问内网地址；确有需要请显式启用");
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("无法解析 Webhook URL 主机");
        }
    }

    private String generateSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("生成签名失败: {}", e.getMessage());
            return "";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
