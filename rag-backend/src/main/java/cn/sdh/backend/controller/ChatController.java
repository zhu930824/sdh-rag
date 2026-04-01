package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.AskRequest;
import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.service.ChatService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 聊天控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    /**
     * 发起问答（流式响应）
     */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> ask(@RequestBody AskRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Flux.just("data: {\"type\":\"error\",\"message\":\"未授权\"}\n\n");
        }

        String sessionId = request.getSessionId();
        Long knowledgeId = request.getKnowledgeId();
        return chatService.ask(request.getQuestion(), sessionId, userId, knowledgeId);
    }

    /**
     * 获取历史对话
     */
    @GetMapping("/history")
    public Result<Page<ChatHistory>> getHistory(
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        Page<ChatHistory> result = chatService.getHistory(sessionId, page, size, userId);
        return Result.success(result);
    }

    /**
     * 删除对话
     */
    @DeleteMapping("/session/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        chatService.deleteSession(sessionId, userId);
        return Result.success("删除成功", null);
    }
}
