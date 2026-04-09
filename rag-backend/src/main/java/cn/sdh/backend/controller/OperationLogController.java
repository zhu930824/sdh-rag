package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.OperationLog;
import cn.sdh.backend.service.OperationLogService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/log")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    @GetMapping("/list")
    public Result<IPage<OperationLog>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        IPage<OperationLog> result = operationLogService.getPage(page, pageSize, type, username, status, startTime, endTime);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<OperationLog> getById(@PathVariable Long id) {
        OperationLog log = operationLogService.getById(id);
        if (log == null) {
            return Result.notFound("日志不存在");
        }
        return Result.success(log);
    }

    @PostMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean success = operationLogService.removeById(id);
        if (success) {
            return Result.success("删除成功", null);
        }
        return Result.error("删除失败");
    }

    @PostMapping("/batch-delete")
    public Result<Map<String, Integer>> batchDelete(@RequestBody Map<String, Long[]> request) {
        Long[] ids = request.get("ids");
        if (ids == null || ids.length == 0) {
            return Result.paramError("请选择要删除的日志");
        }

        int successCount = 0;
        int failCount = 0;
        for (Long id : ids) {
            if (operationLogService.removeById(id)) {
                successCount++;
            } else {
                failCount++;
            }
        }

        Map<String, Integer> data = new HashMap<>();
        data.put("success", successCount);
        data.put("fail", failCount);
        return Result.success("批量删除完成", data);
    }

    @PostMapping("/clear")
    public Result<Void> clearLogs() {
        operationLogService.clearAll();
        return Result.success("清空日志成功", null);
    }
}