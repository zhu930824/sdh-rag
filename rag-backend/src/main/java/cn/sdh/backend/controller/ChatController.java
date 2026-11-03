package cn.sdh.backend.controller;

import cn.sdh.backend.agent.AgentAskService;
import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.AskRequest;
import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.entity.ChatSession;
import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.mapper.AgentRunMapper;
import cn.sdh.backend.service.ChatService;
import cn.sdh.backend.service.ChatSessionService;
import cn.sdh.backend.service.QaFeedbackService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    @Autowired private AgentRunMapper agentRuns;

    @Autowired
    private ChatService chatService;

    @Autowired
    private QaFeedbackService qaFeedbackService;

    @Autowired
    private AgentAskService agentAskService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 发起问答（流式响应）。开启 Agent 模式且模型供应商受支持时走 Agent 决策循环。
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
        boolean memoryEnabled = !Boolean.FALSE.equals(request.getMemoryEnabled());
        Map<String, Object> metadataFilters = request.getMetadataFilter() == null
                ? Map.of()
                : request.getMetadataFilter().toSearchFilters();

        if (request.getAgent() != null && Boolean.TRUE.equals(request.getAgent().getEnabled())) {
            return agentAskService.ask(request.getQuestion(), sessionId, userId, knowledgeId, modelId,
                    memoryEnabled, metadataFilters, request.getAgent());
        }
        return chatService.ask(request.getQuestion(), sessionId, userId, knowledgeId, modelId,
                memoryEnabled, metadataFilters);
    }

    /**
     * 获取会话列表(chat_session 档案行为主,兼容历史会话的动态分组,两者同源)。
     * 标题以档案行为准(用户可改名),messageCount/时间由档案行承担,缺失时回退首问分组。
     */
    @GetMapping("/sessions")
    public Result<Map<String, Object>> getSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        // chat_session 档案行(未归档)
        List<ChatSession> sessionRows = chatSessionService.list(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getIsArchived, 0));
        Map<String, ChatSession> rowBySession = sessionRows.stream()
                .collect(Collectors.toMap(ChatSession::getSessionId, s -> s, (a, b) -> a));

        // 历史消息按 session 分组(兼容 chat_session 建档前的会话)
        Page<ChatHistory> historyPage = chatService.getHistory(null, 1, 1000, userId);
        List<ChatHistory> records = historyPage.getRecords();
        Map<String, List<ChatHistory>> groupedBySession = records.stream()
                .filter(h -> h.getSessionId() != null)
                .collect(Collectors.groupingBy(ChatHistory::getSessionId, LinkedHashMap::new, Collectors.toList()));

        Map<String, Map<String, Object>> sessionsById = new LinkedHashMap<>();
        for (ChatSession row : sessionRows) {
            String sessionId = row.getSessionId();
            List<ChatHistory> historyList = groupedBySession.getOrDefault(sessionId, List.of());
            Map<String, Object> session = new HashMap<>();
            session.put("id", sessionId);
            session.put("title", blank(row.getTitle()) ? "新对话" : row.getTitle());
            session.put("createTime", row.getCreateTime());
            session.put("updateTime", row.getLastMessageTime() != null ? row.getLastMessageTime() : row.getUpdateTime());
            session.put("messageCount", row.getMessageCount() != null && row.getMessageCount() > 0
                    ? row.getMessageCount()
                    : historyList.size());
            session.put("isArchived", row.getIsArchived());
            sessionsById.put(sessionId, session);
        }

        // 历史消息中存在但尚无档案行的会话(迁移前旧数据)按首问标题补齐
        for (Map.Entry<String, List<ChatHistory>> entry : groupedBySession.entrySet()) {
            String sessionId = entry.getKey();
            if (sessionsById.containsKey(sessionId)) {
                continue;
            }
            List<ChatHistory> historyList = entry.getValue();
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
                ChatHistory last = historyList.get(historyList.size() - 1);
                updateTime = last.getCreateTime();
            }
            Map<String, Object> session = new HashMap<>();
            session.put("id", sessionId);
            session.put("title", title);
            session.put("createTime", createTime);
            session.put("updateTime", updateTime);
            session.put("messageCount", historyList.size());
            session.put("isArchived", 0);
            sessionsById.put(sessionId, session);
        }

        // 按最近活动时间倒序
        List<Map<String, Object>> sessions = new ArrayList<>(sessionsById.values());
        sessions.sort((a, b) -> {
            LocalDateTime timeA = toTime(a.get("updateTime"));
            LocalDateTime timeB = toTime(b.get("updateTime"));
            if (timeA == null && timeB == null) return 0;
            if (timeA == null) return 1;
            if (timeB == null) return -1;
            return timeB.compareTo(timeA);
        });

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

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private LocalDateTime toTime(Object value) {
        return value instanceof LocalDateTime time ? time : null;
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
        List<String> runKeys = history.getRecords().stream().map(ChatHistory::getAgentRunKey)
                .filter(Objects::nonNull).distinct().toList();
        Map<String, String> runStatuses = runKeys.isEmpty() ? Map.of() : agentRuns.selectList(new LambdaQueryWrapper<AgentRun>()
                        .eq(AgentRun::getUserId, userId).in(AgentRun::getRunKey, runKeys)).stream()
                .collect(Collectors.toMap(AgentRun::getRunKey, AgentRun::getStatus));

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
                        // 证据链回放:来源 / 路由 / 所用知识库与模型 / Agent 痕迹
                        aiMsg.put("sources", parseJson(h.getSources()));
                        aiMsg.put("routerInfo", parseJson(h.getRouterInfo()));
                        aiMsg.put("knowledgeId", h.getKnowledgeId());
                        aiMsg.put("modelId", h.getModelId());
                        aiMsg.put("agentRunKey", h.getAgentRunKey());
                        aiMsg.put("agentStatus", runStatuses.get(h.getAgentRunKey()));
                        aiMsg.put("agentTrace", parseJson(h.getAgentTrace()));
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

    /** JSON 列回放解析(失败/为空返回 null,前端按缺省处理) */
    private Object parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return objectMapper.convertValue(node, Object.class);
        } catch (Exception e) {
            return null;
        }
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
     * 更新会话标题(chat_session 档案行不存在时按该标题建档,保证改名持久化)
     */
    @PostMapping("/session/{sessionId}/title")
    public Result<Void> updateSessionTitle(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {

        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        String title = request == null ? null : request.get("title");
        if (title == null || title.isBlank()) {
            return Result.paramError("标题不能为空");
        }
        ChatSession row = chatSessionService.getBySessionId(sessionId);
        if (row == null) {
            chatSessionService.createSessionFor(sessionId, userId, title.trim(), null);
        } else {
            if (!userId.equals(row.getUserId())) {
                return Result.forbidden("无权修改该会话");
            }
            chatSessionService.updateSessionTitle(sessionId, title.trim());
        }
        return Result.success("更新成功", null);
    }

    /**
     * 删除对话(chat_history + chat_session 档案行双清)
     */
    @PostMapping("/session/delete/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        chatService.deleteSession(sessionId, userId);
        chatSessionService.deleteOwned(sessionId, userId);
        return Result.success("删除成功", null);
    }
}
