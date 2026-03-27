package cn.sdh.backend.service;

import cn.sdh.backend.entity.ChatSession;
import cn.sdh.backend.entity.SessionShare;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ChatSessionService extends IService<ChatSession> {

    IPage<ChatSession> getUserSessions(Long userId, Integer page, Integer pageSize);

    List<ChatSession> getStarredSessions(Long userId);

    List<ChatSession> getArchivedSessions(Long userId);

    ChatSession getBySessionId(String sessionId);

    ChatSession createSession(Long userId, String title, Long modelId, Long promptTemplateId);

    void updateSessionTitle(String sessionId, String title);

    void updateMessageStats(String sessionId, Integer tokens);

    void toggleStar(String sessionId);

    void toggleArchive(String sessionId);

    void deleteSession(String sessionId);

    SessionShare createShare(String sessionId, Long userId, String password, Integer expireHours);

    SessionShare getShareByCode(String shareCode);

    ChatSession getSharedSession(String shareCode, String password);

    void closeShare(Long shareId);

    List<SessionShare> getSessionShares(String sessionId);

    byte[] exportSession(String sessionId, String format);

    String generateSessionTitle(String firstMessage);
}
