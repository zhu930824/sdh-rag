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

    /** 以指定 sessionId 建档(问答写路径自动建档用,避免 UUID 与 chat_history 脱钩) */
    ChatSession createSessionFor(String sessionId, Long userId, String title, Long modelId);

    void updateSessionTitle(String sessionId, String title);

    void updateMessageStats(String sessionId, Integer tokens);

    void toggleStar(String sessionId);

    void toggleArchive(String sessionId);

    void deleteSession(String sessionId);

    /**
     * 问答写路径自动建档:会话行不存在则按首问生成标题创建,存在则消息数/最后消息时间自增。
     */
    void touch(String sessionId, Long userId, String firstQuestion, Long modelId);

    /** 删除本人会话(连带清理 chat_history);非本人抛业务异常 */
    void deleteOwned(String sessionId, Long userId);

    SessionShare createShare(String sessionId, Long userId, String password, Integer expireHours);

    SessionShare getShareByCode(String shareCode);

    ChatSession getSharedSession(String shareCode, String password);

    void closeShare(Long shareId);

    List<SessionShare> getSessionShares(String sessionId);

    byte[] exportSession(String sessionId, String format);

    String generateSessionTitle(String firstMessage);
}
