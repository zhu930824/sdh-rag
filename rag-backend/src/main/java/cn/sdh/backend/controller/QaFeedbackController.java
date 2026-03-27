package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.QaFeedback;
import cn.sdh.backend.service.QaFeedbackService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class QaFeedbackController {

    private final QaFeedbackService feedbackService;

    @GetMapping("/list")
    public Result<IPage<QaFeedback>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Integer status) {
        return Result.success(feedbackService.getPage(page, pageSize, rating, status));
    }

    @PostMapping
    public Result<QaFeedback> create(@Valid @RequestBody QaFeedback feedback) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        Long chatUserId = feedbackService.getUserIdByChatHistoryId(feedback.getChatHistoryId());
        if (!userId.equals(chatUserId)) {
            return Result.forbidden("无权评价此问答");
        }
        
        feedback.setUserId(userId);
        return Result.success(feedbackService.createFeedback(feedback));
    }

    @PutMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestParam Integer status) {
        Long handlerId = UserContext.getCurrentUserId();
        if (handlerId == null) {
            return Result.unauthorized();
        }
        feedbackService.handleFeedback(id, handlerId, status);
        return Result.success();
    }

    @GetMapping("/stats/{chatHistoryId}")
    public Result<Map<String, Object>> stats(@PathVariable Long chatHistoryId) {
        return Result.success(feedbackService.getFeedbackStats(chatHistoryId));
    }
}