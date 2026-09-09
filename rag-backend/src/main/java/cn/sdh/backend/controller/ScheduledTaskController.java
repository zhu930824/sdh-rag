package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.ScheduledTask;
import cn.sdh.backend.service.ScheduledTaskService;
import cn.sdh.backend.service.PlatformAdminService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/scheduled-task")
@RequiredArgsConstructor
public class ScheduledTaskController {

    private final ScheduledTaskService scheduledTaskService;
    private final PlatformAdminService adminService;

    @GetMapping("/list")
    public Result<IPage<ScheduledTask>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Integer status) {
        adminService.requireAdmin();
        return Result.success(scheduledTaskService.getPage(page, pageSize, taskType, status));
    }

    @GetMapping("/{id}")
    public Result<ScheduledTask> getById(@PathVariable Long id) {
        adminService.requireAdmin();
        ScheduledTask task = scheduledTaskService.getById(id);
        if (task == null) {
            return Result.notFound("任务不存在");
        }
        return Result.success(task);
    }

    @PostMapping
    public Result<ScheduledTask> create(@Valid @RequestBody ScheduledTask task) {
        adminService.requireAdmin();
        try {
            ScheduledTask created = scheduledTaskService.createTask(task);
            return Result.success(created);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/update/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ScheduledTask task) {
        adminService.requireAdmin();
        task.setId(id);
        try {
            scheduledTaskService.updateTask(task);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/toggle/{id}")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        adminService.requireAdmin();
        try {
            scheduledTaskService.toggleStatus(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/execute")
    public Result<Void> execute(@PathVariable Long id) {
        adminService.requireAdmin();
        try {
            scheduledTaskService.executeTask(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminService.requireAdmin();
        try {
            scheduledTaskService.deleteTask(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
