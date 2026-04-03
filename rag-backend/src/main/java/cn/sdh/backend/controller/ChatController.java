package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.AskRequest;
import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.service.ChatService;
import cn.sdh.backend.service.QaFeedbackService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 聊天控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private QaFeedbackService qaFeedbackService;

    /**
     * 发起问答（流式响应）
     */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> ask(@RequestBody AskRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Flux.just("{\"type\":\"error\",\"message\":\"未授权\"}");
        }

        String sessionId = request.getSessionId();
        Long knowledgeId = request.getKnowledgeId();
        Long modelId = request.getModelId();
        return chatService.ask(request.getQuestion(), sessionId, userId, knowledgeId, modelId);
    }

    /**
     * 获取会话列表（按session分组）
     */
    @GetMapping("/sessions")
    public Result<Map<String, Object>> getSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        // 获取所有聊天记录
        Page<ChatHistory> historyPage = chatService.getHistory(null, 1, 1000, userId);
        List<ChatHistory> records = historyPage.getRecords();

        // 按 sessionId 分组
        Map<String, List<ChatHistory>> groupedBySession = records.stream()
                .filter(h -> h.getSessionId() != null)
                .collect(Collectors.groupingBy(ChatHistory::getSessionId, LinkedHashMap::new, Collectors.toList()));

        // 转换为会话列表
        List<Map<String, Object>> sessions = new ArrayList<>();
        for (Map.Entry<String, List<ChatHistory>> entry : groupedBySession.entrySet()) {
            String sessionId = entry.getKey();
            List<ChatHistory> historyList = entry.getValue();

            // 获取第一条消息作为标题
            String title = "新对话";
            LocalDateTime createTime = null;
            LocalDateTime updateTime = null;

            if (!historyList.isEmpty()) {
                ChatHistory first = historyList.get(0);
                if (first.getQuestion() != null && !first.getQuestion().isEmpty()) {
                    title = first.getQuestion().length() > 30
                            ? first.getQuestion().substring(0, 30) + "..."
                            : first.getQuestion();
                }
                createTime = first.getCreateTime();

                // 最后一条消息的时间作为更新时间
                ChatHistory last = historyList.get(historyList.size() - 1);
                updateTime = last.getCreateTime();
            }

            Map<String, Object> session = new HashMap<>();
            session.put("id", sessionId);
            session.put("title", title);
            session.put("createTime", createTime);
            session.put("updateTime", updateTime);
            session.put("messageCount", historyList.size());
            sessions.add(session);
        }

        // 分页处理
        int total = sessions.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);

        List<Map<String, Object>> pagedSessions = fromIndex < total
                ? sessions.subList(fromIndex, toIndex)
                : new ArrayList<>();

        Map<String, Object> result = new HashMap<>();
        result.put("records", pagedSessions);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    /**
     * 获取历史对话（兼容旧接口）
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
     * 获取会话消息
     */
    @GetMapping("/session/{sessionId}/messages")
    public Result<List<Map<String, Object>>> getSessionMessages(@PathVariable String sessionId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        Page<ChatHistory> history = chatService.getHistory(sessionId, 1, 1000, userId);

        // 转换为前端需要的消息格式，按时间正序排列
        List<Map<String, Object>> messages = history.getRecords().stream()
                .sorted(Comparator.comparing(ChatHistory::getCreateTime))
                .flatMap(h -> {
                    List<Map<String, Object>> msgs = new ArrayList<>();

                    Map<String, Object> userMsg = new HashMap<>();
                    userMsg.put("id", h.getId() + "_user");
                    userMsg.put("role", "user");
                    userMsg.put("content", h.getQuestion());
                    userMsg.put("createTime", h.getCreateTime());
                    msgs.add(userMsg);

                    if (h.getAnswer() != null && !h.getAnswer().isEmpty()) {
                        Map<String, Object> aiMsg = new HashMap<>();
                        aiMsg.put("id", h.getId() + "_assistant");
                        aiMsg.put("role", "assistant");
                        aiMsg.put("content", h.getAnswer());
                        aiMsg.put("createTime", h.getCreateTime());
                        aiMsg.put("historyId", h.getId());
                        // 获取用户对该条消息的评价
                        Integer userRating = qaFeedbackService.getUserRating(h.getId(), userId);
                        aiMsg.put("userRating", userRating);
                        msgs.add(aiMsg);
                    }

                    return msgs.stream();
                })
                .collect(Collectors.toList());

        return Result.success(messages);
    }

    /**
     * 创建新会话
     */
    @PostMapping("/session")
    public Result<Map<String, Object>> createSession() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        // 创建一个新会话
        String sessionId = UUID.randomUUID().toString();

        Map<String, Object> session = new HashMap<>();
        session.put("id", sessionId);
        session.put("title", "新对话");
        session.put("createTime", LocalDateTime.now());
        session.put("updateTime", LocalDateTime.now());

        return Result.success(session);
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/session/{sessionId}/title")
    public Result<Void> updateSessionTitle(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {

        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        // TODO: 实现更新会话标题的逻辑
        return Result.success("更新成功", null);
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
