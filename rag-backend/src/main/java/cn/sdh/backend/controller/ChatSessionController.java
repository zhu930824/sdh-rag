package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.ChatSession;
import cn.sdh.backend.entity.SessionShare;
import cn.sdh.backend.service.ChatSessionService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 会话管理(/api/chat-session):列表/星标/归档/改名/删除/分享/导出。
 * 之前该页面全部端点在后端缺失(404 死页);这里委托已实现的 ChatSessionServiceImpl 并补归属校验。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat-session")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    @GetMapping("/list")
    public Result<IPage<ChatSession>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        if (pageSize > 100) pageSize = 100;
        Long userId = requireUser();
        return Result.success(chatSessionService.getUserSessions(userId, page, pageSize));
    }

    @GetMapping("/starred")
    public Result<List<ChatSession>> starred() {
        return Result.success(chatSessionService.getStarredSessions(requireUser()));
    }

    @GetMapping("/archived")
    public Result<List<ChatSession>> archived() {
        return Result.success(chatSessionService.getArchivedSessions(requireUser()));
    }

    @GetMapping("/{sessionId}")
    public Result<ChatSession> detail(@PathVariable String sessionId) {
        ChatSession session = owned(sessionId, requireUser());
        return Result.success(session);
    }

    @PostMapping
    public Result<ChatSession> create(@RequestBody(required = false) Map<String, Object> request) {
        String title = request == null ? null : stringOf(request.get("title"));
        Long modelId = longOf(request == null ? null : request.get("modelId"));
        Long promptTemplateId = longOf(request == null ? null : request.get("promptTemplateId"));
        return Result.success("会话已创建", chatSessionService.createSession(requireUser(), title, modelId, promptTemplateId));
    }

    @PostMapping("/title/{sessionId}")
    public Result<Void> updateTitle(@PathVariable String sessionId, @RequestBody Map<String, String> body) {
        ChatSession session = owned(sessionId, requireUser());
        String title = body == null ? null : body.get("title");
        if (title == null || title.isBlank()) {
            throw new BusinessException(400, "标题不能为空");
        }
        chatSessionService.updateSessionTitle(session.getSessionId(), title.trim());
        return Result.success("标题已更新", null);
    }

    @PostMapping("/star/{sessionId}")
    public Result<Void> toggleStar(@PathVariable String sessionId) {
        ChatSession session = owned(sessionId, requireUser());
        chatSessionService.toggleStar(session.getSessionId());
        return Result.success("已更新收藏状态", null);
    }

    @PostMapping("/archive/{sessionId}")
    public Result<Void> toggleArchive(@PathVariable String sessionId) {
        ChatSession session = owned(sessionId, requireUser());
        chatSessionService.toggleArchive(session.getSessionId());
        return Result.success("已更新归档状态", null);
    }

    @PostMapping("/delete/{sessionId}")
    public Result<Void> delete(@PathVariable String sessionId) {
        chatSessionService.deleteOwned(sessionId, requireUser());
        return Result.success("删除成功", null);
    }

    // ---------- 分享与导出 ----------

    @PostMapping("/{sessionId}/share")
    public Result<SessionShare> createShare(@PathVariable String sessionId,
                                            @RequestBody(required = false) Map<String, Object> body) {
        Long userId = requireUser();
        owned(sessionId, userId);
        String password = body == null ? null : stringOf(body.get("password"));
        Integer expireHours = body == null ? null : intOf(body.get("expireHours"));
        return Result.success("分享已创建", chatSessionService.createShare(sessionId, userId, password, expireHours));
    }

    @GetMapping("/{sessionId}/shares")
    public Result<List<SessionShare>> shares(@PathVariable String sessionId) {
        owned(sessionId, requireUser());
        return Result.success(chatSessionService.getSessionShares(sessionId));
    }

    @GetMapping("/shared/{shareCode}")
    public Result<ChatSession> shared(@PathVariable String shareCode,
                                      @RequestParam(required = false) String password) {
        return Result.success(chatSessionService.getSharedSession(shareCode, password));
    }

    @PostMapping("/share/delete/{shareId}")
    public Result<Void> closeShare(@PathVariable Long shareId) {
        chatSessionService.closeShare(shareId);
        return Result.success("分享已关闭", null);
    }

    @GetMapping(value = "/{sessionId}/export")
    public ResponseEntity<byte[]> export(@PathVariable String sessionId,
                                         @RequestParam(defaultValue = "markdown") String format) {
        owned(sessionId, requireUser());
        byte[] bytes = chatSessionService.exportSession(sessionId, format);
        String filename = "session-" + sessionId.substring(0, Math.min(8, sessionId.length()));
        String contentType = "json".equalsIgnoreCase(format)
                ? "application/json;charset=UTF-8" : "text/markdown;charset=UTF-8";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + filename + "." + ("json".equalsIgnoreCase(format) ? "json" : "md"))
                .body(bytes);
    }

    private ChatSession owned(String sessionId, Long userId) {
        ChatSession session = chatSessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!userId.equals(session.getUserId())) {
            throw new BusinessException(403, "无权访问该会话");
        }
        return session;
    }

    private Long requireUser() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }

    private String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longOf(Object value) {
        if (value == null) return null;
        try {
            return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer intOf(Object value) {
        if (value == null) return null;
        try {
            return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
