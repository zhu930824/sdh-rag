package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.WebhookConfig;
import cn.sdh.backend.service.PlatformAdminService;
import cn.sdh.backend.service.WebhookService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;
    private final PlatformAdminService adminService;

    @GetMapping("/list")
    public Result<IPage<WebhookConfig>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        adminService.requireAdmin();
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        return Result.success(webhookService.getPage(page, safePageSize, status).convert(this::sanitize));
    }

    @GetMapping("/active")
    public Result<List<WebhookConfig>> active() {
        adminService.requireAdmin();
        return Result.success(webhookService.getActiveWebhooks().stream().map(this::sanitize).toList());
    }

    @GetMapping("/{id}")
    public Result<WebhookConfig> get(@PathVariable Long id) {
        adminService.requireAdmin();
        WebhookConfig webhook = webhookService.getById(id);
        return webhook == null ? Result.notFound("Webhook不存在") : Result.success(sanitize(webhook));
    }

    @PostMapping
    public Result<WebhookConfig> create(@RequestBody WebhookConfig webhook) {
        adminService.requireAdmin();
        return Result.success(sanitize(webhookService.createWebhook(webhook)));
    }

    @PostMapping("/update/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody WebhookConfig webhook) {
        adminService.requireAdmin();
        webhook.setId(id);
        webhookService.updateWebhook(webhook);
        return Result.success();
    }

    @PostMapping("/toggle/{id}")
    public Result<Void> toggle(@PathVariable Long id) {
        adminService.requireAdmin();
        webhookService.toggleStatus(id);
        return Result.success();
    }

    @PostMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminService.requireAdmin();
        webhookService.deleteWebhook(id);
        return Result.success();
    }

    @PostMapping("/{id}/test")
    public Result<Void> test(@PathVariable Long id) {
        adminService.requireAdmin();
        webhookService.testWebhook(id);
        return Result.success();
    }

    @PostMapping("/trigger")
    public Result<Void> trigger(@RequestBody TriggerRequest request) {
        adminService.requireAdmin();
        if (request == null || request.event() == null
                || !request.event().matches("[a-z][a-z0-9_.-]{1,127}")) {
            throw new BusinessException(400, "Webhook 事件名称格式不正确");
        }
        webhookService.triggerWebhooks(request.event(), request.payload() == null ? Map.of() : request.payload());
        return Result.success();
    }

    private WebhookConfig sanitize(WebhookConfig source) {
        WebhookConfig target = new WebhookConfig();
        BeanUtils.copyProperties(source, target);
        target.setSecret(null);
        target.setHeaders(null);
        return target;
    }

    public record TriggerRequest(String event, Map<String, Object> payload) {}
}
