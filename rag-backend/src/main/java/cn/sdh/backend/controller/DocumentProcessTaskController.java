package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.DocumentProcessTask;
import cn.sdh.backend.service.DocumentProcessTaskService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/process-task")
@RequiredArgsConstructor
public class DocumentProcessTaskController {

    private final DocumentProcessTaskService documentProcessTaskService;

    @GetMapping("/list")
    public Result<IPage<DocumentProcessTask>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long documentId,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Integer status) {
        return Result.success(documentProcessTaskService.getPage(page, pageSize, documentId, taskType, status));
    }

    @GetMapping("/{id}")
    public Result<DocumentProcessTask> getById(@PathVariable Long id) {
        DocumentProcessTask task = documentProcessTaskService.getById(id);
        if (task == null) {
            return Result.notFound("任务不存在");
        }
        return Result.success(task);
    }

    @PostMapping
    public Result<DocumentProcessTask> create(@Valid @RequestBody DocumentProcessTask task) {
        DocumentProcessTask created = documentProcessTaskService.createTask(task.getDocumentId(), task.getTaskType());
        return Result.success(created);
    }

    @PostMapping("/{id}/retry")
    public Result<Void> retry(@PathVariable Long id) {
        try {
            documentProcessTaskService.retryTask(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/document/{documentId}")
    public Result<IPage<DocumentProcessTask>> listByDocument(
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(documentProcessTaskService.getPage(page, pageSize, documentId, null, null));
    }
}
