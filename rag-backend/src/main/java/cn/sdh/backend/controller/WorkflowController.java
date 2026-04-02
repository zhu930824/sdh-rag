package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.Workflow;
import cn.sdh.backend.service.WorkflowService;
import cn.sdh.backend.workflow.dto.ExecutionEvent;
import cn.sdh.backend.workflow.dto.ExecutionResponse;
import cn.sdh.backend.workflow.executor.WorkflowEngine;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowEngine workflowEngine;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

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
    public Result<Workflow> save(@Valid @RequestBody Workflow workflow) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        workflow.setUserId(userId);
        workflowService.save(workflow);
        return Result.success(workflow);
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

    /**
     * 同步执行工作流
     */
    @PostMapping("/{id}/execute")
    public Result<ExecutionResponse> execute(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> request) {

        Workflow workflow = workflowService.getById(id);
        if (workflow == null) {
            return Result.notFound("工作流不存在");
        }

        String inputData = request != null && request.containsKey("inputs")
            ? request.get("inputs").toString()
            : "";

        ExecutionResponse response = workflowEngine.execute(workflow, inputData);
        return Result.success(response);
    }

    /**
     * SSE 流式执行工作流
     */
    @GetMapping(value = "/{id}/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeStream(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "") String inputData) {

        Workflow workflow = workflowService.getById(id);
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        if (workflow == null) {
            executorService.execute(() -> {
                try {
                    emitter.send(SseEmitter.event()
                        .name("ERROR")
                        .data("{\"message\":\"工作流不存在\"}"));
                    emitter.complete();
                } catch (IOException e) {
                    log.error("SSE 发送失败", e);
                }
            });
            return emitter;
        }

        final String finalInputData = inputData;

        executorService.execute(() -> {
            try {
                ExecutionResponse response = workflowEngine.executeWithCallback(
                    workflow,
                    finalInputData,
                    event -> {
                        try {
                            String eventJson = cn.sdh.backend.workflow.dto.ExecutionEvent.class
                                .getMethod("getEventType")
                                .invoke(event) != null
                                ? toJson(event)
                                : "{}";

                            emitter.send(SseEmitter.event()
                                .name(event.getEventType())
                                .data(eventJson));
                        } catch (Exception e) {
                            log.error("SSE 发送事件失败", e);
                        }
                    }
                );

                // 发送最终结果
                emitter.send(SseEmitter.event()
                    .name("COMPLETE")
                    .data(toJson(response)));
                emitter.complete();

            } catch (Exception e) {
                log.error("工作流执行失败", e);
                try {
                    emitter.send(SseEmitter.event()
                        .name("ERROR")
                        .data("{\"message\":\"" + e.getMessage() + "\"}"));
                    emitter.completeWithError(e);
                } catch (IOException ioException) {
                    log.error("SSE 发送错误失败", ioException);
                }
            }
        });

        emitter.onCompletion(() -> log.debug("SSE 连接关闭: workflowId={}", id));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时: workflowId={}", id);
            emitter.complete();
        });

        return emitter;
    }

    /**
     * 简单的 JSON 序列化
     */
    private String toJson(Object obj) {
        try {
            return com.alibaba.fastjson2.JSON.toJSONString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
