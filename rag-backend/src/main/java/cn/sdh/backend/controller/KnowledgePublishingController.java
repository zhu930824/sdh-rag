package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.KnowledgeDraft;
import cn.sdh.backend.entity.KnowledgePublishRecord;
import cn.sdh.backend.service.KnowledgePublishingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/knowledge-publishing")
public class KnowledgePublishingController {
    private final KnowledgePublishingService service;

    @GetMapping("/drafts")
    public Result<List<KnowledgeDraft>> list(@RequestParam(required = false) String status) {
        return Result.success(service.list(status));
    }

    @GetMapping("/drafts/{id}/records")
    public Result<List<KnowledgePublishRecord>> records(@PathVariable Long id) {
        return Result.success(service.records(id));
    }

    @PostMapping("/drafts")
    public Result<KnowledgeDraft> create(@RequestBody KnowledgePublishingService.DraftCommand command) {
        return Result.success("知识草稿已创建", service.create(command));
    }

    @PostMapping("/drafts/{id}")
    public Result<KnowledgeDraft> update(@PathVariable Long id,
                                         @RequestBody KnowledgePublishingService.DraftCommand command) {
        return Result.success("知识草稿已保存", service.update(id, command));
    }

    @PostMapping("/drafts/{id}/submit")
    public Result<KnowledgeDraft> submit(@PathVariable Long id) {
        return Result.success("知识草稿已提交审核", service.submit(id));
    }

    @PostMapping("/drafts/{id}/review")
    public Result<KnowledgeDraft> review(@PathVariable Long id,
                                         @RequestBody KnowledgePublishingService.ReviewCommand command) {
        return Result.success("审核已完成", service.review(id, command));
    }

    @PostMapping("/drafts/{id}/publish")
    public Result<KnowledgeDraft> publish(@PathVariable Long id) {
        return Result.success("已提交增量索引", service.publish(id));
    }

    @PostMapping("/drafts/{id}/validate")
    public Result<KnowledgePublishingService.ValidationResult> validate(@PathVariable Long id) {
        return Result.success("回归验证已完成", service.validate(id));
    }

    @PostMapping("/drafts/{id}/rollback")
    public Result<KnowledgeDraft> rollback(@PathVariable Long id) {
        return Result.success("知识已回滚", service.rollback(id));
    }
}
