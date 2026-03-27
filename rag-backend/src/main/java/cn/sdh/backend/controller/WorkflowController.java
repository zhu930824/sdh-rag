package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.Workflow;
import cn.sdh.backend.service.WorkflowService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @GetMapping("/list")
    public Result<IPage<Workflow>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        
        if (pageSize > 100) pageSize = 100;
        
        IPage<Workflow> result = workflowService.getPage(page, pageSize, keyword);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Workflow> getById(@PathVariable Long id) {
        Workflow workflow = workflowService.getById(id);
        if (workflow == null) {
            return Result.notFound("工作流不存在");
        }
        return Result.success(workflow);
    }

    @PostMapping
    public Result<Void> save(@Valid @RequestBody Workflow workflow) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        workflow.setUserId(userId);
        workflowService.save(workflow);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody Workflow workflow) {
        Workflow existing = workflowService.getById(id);
        if (existing == null) {
            return Result.notFound("工作流不存在");
        }
        
        Long userId = UserContext.getCurrentUserId();
        if (userId == null || !userId.equals(existing.getUserId())) {
            return Result.forbidden("无权修改此工作流");
        }
        
        workflow.setId(id);
        workflow.setUserId(existing.getUserId());
        workflowService.update(workflow);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Workflow existing = workflowService.getById(id);
        if (existing == null) {
            return Result.notFound("工作流不存在");
        }
        
        Long userId = UserContext.getCurrentUserId();
        if (userId == null || !userId.equals(existing.getUserId())) {
            return Result.forbidden("无权删除此工作流");
        }
        
        workflowService.deleteById(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        Workflow existing = workflowService.getById(id);
        if (existing == null) {
            return Result.notFound("工作流不存在");
        }
        
        Long userId = UserContext.getCurrentUserId();
        if (userId == null || !userId.equals(existing.getUserId())) {
            return Result.forbidden("无权修改此工作流");
        }
        
        workflowService.toggleStatus(id);
        return Result.success();
    }
}