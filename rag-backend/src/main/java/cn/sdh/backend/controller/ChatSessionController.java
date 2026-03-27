package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.ChatSession;
import cn.sdh.backend.entity.SessionShare;
import cn.sdh.backend.service.ChatSessionService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat-session")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    @GetMapping("/list")
    public Result<IPage<ChatSession>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        return Result.success(chatSessionService.getUserSessions(userId, page, pageSize));
    }

    @GetMapping("/starred")
    public Result<List<ChatSession>> starredList() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        return Result.success(chatSessionService.getStarredSessions(userId));
    }

    @GetMapping("/archived")
    public Result<List<ChatSession>> archivedList() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        return Result.success(chatSessionService.getArchivedSessions(userId));
    }

    @GetMapping("/{sessionId}")
    public Result<ChatSession> getBySessionId(@PathVariable String sessionId) {
        ChatSession session = chatSessionService.getBySessionId(sessionId);
        if (session == null) {
            return Result.notFound("会话不存在");
        }
        return Result.success(session);
    }

    @PostMapping
    public Result<ChatSession> create(@RequestBody Map<String, Object> request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        String title = (String) request.get("title");
        Long modelId = request.get("modelId") != null ? ((Number) request.get("modelId")).longValue() : null;
        Long promptTemplateId = request.get("promptTemplateId") != null ? 
            ((Number) request.get("promptTemplateId")).longValue() : null;

        ChatSession session = chatSessionService.createSession(userId, title, modelId, promptTemplateId);
        return Result.success(session);
    }

    @PutMapping("/{sessionId}/title")
    public Result<Void> updateTitle(@PathVariable String sessionId, @RequestBody Map<String, String> request) {
        String title = request.get("title");
        chatSessionService.updateSessionTitle(sessionId, title);
        return Result.success();
    }

    @PutMapping("/{sessionId}/star")
    public Result<Void> toggleStar(@PathVariable String sessionId) {
        chatSessionService.toggleStar(sessionId);
        return Result.success();
    }

    @PutMapping("/{sessionId}/archive")
    public Result<Void> toggleArchive(@PathVariable String sessionId) {
        chatSessionService.toggleArchive(sessionId);
        return Result.success();
    }

    @DeleteMapping("/{sessionId}")
    public Result<Void> delete(@PathVariable String sessionId) {
        chatSessionService.deleteSession(sessionId);
        return Result.success();
    }

    @PostMapping("/{sessionId}/share")
    public Result<SessionShare> createShare(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        String password = request != null ? (String) request.get("password") : null;
        Integer expireHours = request != null ? (Integer) request.get("expireHours") : null;

        try {
            SessionShare share = chatSessionService.createShare(sessionId, userId, password, expireHours);
            return Result.success(share);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{sessionId}/shares")
    public Result<List<SessionShare>> getShares(@PathVariable String sessionId) {
        return Result.success(chatSessionService.getSessionShares(sessionId));
    }

    @GetMapping("/shared/{shareCode}")
    public Result<ChatSession> getSharedSession(
            @PathVariable String shareCode,
            @RequestParam(required = false) String password) {
        try {
            ChatSession session = chatSessionService.getSharedSession(shareCode, password);
            return Result.success(session);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/share/{shareId}")
    public Result<Void> closeShare(@PathVariable Long shareId) {
        chatSessionService.closeShare(shareId);
        return Result.success();
    }

    @GetMapping("/{sessionId}/export")
    public ResponseEntity<byte[]> exportSession(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "markdown") String format) {
        try {
            byte[] content = chatSessionService.exportSession(sessionId, format);
            
            String filename = "session_" + sessionId + "." + format;
            String contentType = "json".equalsIgnoreCase(format) ? 
                MediaType.APPLICATION_JSON_VALUE : "text/markdown";

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(content);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
