package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.ApprovalTask;
import cn.sdh.backend.entity.ApprovalRecord;
import cn.sdh.backend.service.ApprovalService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/pending")
    public Result<IPage<ApprovalTask>> pendingList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(approvalService.getPendingTasks(page, pageSize));
    }

    @GetMapping("/my")
    public Result<IPage<ApprovalTask>> myList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        return Result.success(approvalService.getUserTasks(userId, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getDetail(@PathVariable Long id) {
        ApprovalTask task = approvalService.getById(id);
        if (task == null) {
            return Result.notFound("审核任务不存在");
        }
        
        List<ApprovalRecord> records = approvalService.getTaskRecords(id);
        
        Map<String, Object> result = new HashMap<>();
        result.put("task", task);
        result.put("records", records);
        return Result.success(result);
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, @RequestParam(required = false) String comment) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        try {
            approvalService.approve(id, userId, comment);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestParam(required = false) String comment) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        try {
            approvalService.reject(id, userId, comment);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
