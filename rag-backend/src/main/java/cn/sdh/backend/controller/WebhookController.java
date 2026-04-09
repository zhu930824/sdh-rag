package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.WebhookConfig;
import cn.sdh.backend.service.WebhookService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @GetMapping("/list")
    public Result<IPage<WebhookConfig>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        return Result.success(webhookService.getPage(page, pageSize, status));
    }

    @GetMapping("/active")
    public Result<List<WebhookConfig>> activeList() {
        return Result.success(webhookService.getActiveWebhooks());
    }

    @GetMapping("/{id}")
    public Result<WebhookConfig> getById(@PathVariable Long id) {
        WebhookConfig webhook = webhookService.getById(id);
        if (webhook == null) {
            return Result.notFound("Webhook不存在");
        }
        return Result.success(webhook);
    }

    @PostMapping
    public Result<WebhookConfig> create(@Valid @RequestBody WebhookConfig webhook) {
        try {
            WebhookConfig created = webhookService.createWebhook(webhook);
            return Result.success(created);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/update/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody WebhookConfig webhook) {
        webhook.setId(id);
        try {
            webhookService.updateWebhook(webhook);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/toggle/{id}")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        try {
            webhookService.toggleStatus(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        webhookService.deleteWebhook(id);
        return Result.success();
    }

    @PostMapping("/{id}/test")
    public Result<Void> test(@PathVariable Long id) {
        try {
            webhookService.testWebhook(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/trigger")
    public Result<Void> trigger(@RequestBody Map<String, Object> request) {
        String event = (String) request.get("event");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) request.get("payload");

        webhookService.triggerWebhooks(event, payload);
        return Result.success();
    }
}
