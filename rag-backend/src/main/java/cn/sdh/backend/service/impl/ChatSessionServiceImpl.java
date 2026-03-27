package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.entity.ChatSession;
import cn.sdh.backend.entity.SessionShare;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.mapper.ChatSessionMapper;
import cn.sdh.backend.mapper.SessionShareMapper;
import cn.sdh.backend.service.ChatSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;
    private final SessionShareMapper sessionShareMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final ObjectMapper objectMapper;

    @Override
    public IPage<ChatSession> getUserSessions(Long userId, Integer page, Integer pageSize) {
        Page<ChatSession> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
               .eq(ChatSession::getIsArchived, 0)
               .orderByDesc(ChatSession::getLastMessageTime);
        return page(pageParam, wrapper);
    }

    @Override
    public List<ChatSession> getStarredSessions(Long userId) {
        return chatSessionMapper.selectStarredByUserId(userId);
    }

    @Override
    public List<ChatSession> getArchivedSessions(Long userId) {
        return chatSessionMapper.selectArchivedByUserId(userId);
    }

    @Override
    public ChatSession getBySessionId(String sessionId) {
        return chatSessionMapper.selectBySessionId(sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSession createSession(Long userId, String title, Long modelId, Long promptTemplateId) {
        String sessionId = UUID.randomUUID().toString();

        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTitle(title != null ? title : "新对话");
        session.setModelId(modelId);
        session.setPromptTemplateId(promptTemplateId);
        session.setMessageCount(0);
        session.setTotalTokens(0);
        session.setIsStarred((byte) 0);
        session.setIsArchived((byte) 0);
        session.setLastMessageTime(LocalDateTime.now());
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        save(session);

        return session;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSessionTitle(String sessionId, String title) {
        chatSessionMapper.updateTitle(sessionId, title);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMessageStats(String sessionId, Integer tokens) {
        chatSessionMapper.updateMessageStats(sessionId, tokens != null ? tokens : 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStar(String sessionId) {
        ChatSession session = getBySessionId(sessionId);
        if (session != null) {
            chatSessionMapper.updateStarred(sessionId, (byte) (session.getIsStarred() == 1 ? 0 : 1));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleArchive(String sessionId) {
        ChatSession session = getBySessionId(sessionId);
        if (session != null) {
            session.setIsArchived((byte) (session.getIsArchived() == 1 ? 0 : 1));
            session.setUpdateTime(LocalDateTime.now());
            updateById(session);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String sessionId) {
        ChatSession session = getBySessionId(sessionId);
        if (session != null) {
            removeById(session.getId());

            LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatHistory::getSessionId, sessionId);
            chatHistoryMapper.delete(wrapper);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SessionShare createShare(String sessionId, Long userId, String password, Integer expireHours) {
        ChatSession session = getBySessionId(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在");
        }
        if (!userId.equals(session.getUserId())) {
            throw new RuntimeException("无权分享此会话");
        }

        SessionShare existing = sessionShareMapper.selectActiveBySession(sessionId, userId);
        if (existing != null) {
            return existing;
        }

        String shareCode = generateShareCode();

        SessionShare share = new SessionShare();
        share.setSessionId(sessionId);
        share.setUserId(userId);
        share.setShareCode(shareCode);
        share.setPassword(password);
        if (expireHours != null && expireHours > 0) {
            share.setExpireTime(LocalDateTime.now().plusHours(expireHours));
        }
        share.setViewCount(0);
        share.setStatus((byte) 1);
        share.setCreateTime(LocalDateTime.now());
        sessionShareMapper.insert(share);

        return share;
    }

    @Override
    public SessionShare getShareByCode(String shareCode) {
        SessionShare share = sessionShareMapper.selectByShareCode(shareCode);
        if (share != null && share.getExpireTime() != null && share.getExpireTime().isBefore(LocalDateTime.now())) {
            return null;
        }
        return share;
    }

    @Override
    public ChatSession getSharedSession(String shareCode, String password) {
        SessionShare share = getShareByCode(shareCode);
        if (share == null) {
            throw new RuntimeException("分享链接不存在或已过期");
        }
        if (share.getPassword() != null && !share.getPassword().isEmpty()) {
            if (password == null || !password.equals(share.getPassword())) {
                throw new RuntimeException("访问密码错误");
            }
        }

        sessionShareMapper.incrementViewCount(shareCode);
        return getBySessionId(share.getSessionId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeShare(Long shareId) {
        SessionShare share = sessionShareMapper.selectById(shareId);
        if (share != null) {
            share.setStatus((byte) 0);
            sessionShareMapper.updateById(share);
        }
    }

    @Override
    public List<SessionShare> getSessionShares(String sessionId) {
        LambdaQueryWrapper<SessionShare> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SessionShare::getSessionId, sessionId)
               .eq(SessionShare::getStatus, 1)
               .orderByDesc(SessionShare::getCreateTime);
        return sessionShareMapper.selectList(wrapper);
    }

    @Override
    public byte[] exportSession(String sessionId, String format) {
        ChatSession session = getBySessionId(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在");
        }

        LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatHistory::getSessionId, sessionId)
               .orderByAsc(ChatHistory::getCreateTime);
        List<ChatHistory> historyList = chatHistoryMapper.selectList(wrapper);

        String content;
        if ("json".equalsIgnoreCase(format)) {
            try {
                Map<String, Object> exportData = new HashMap<>();
                exportData.put("session", session);
                exportData.put("history", historyList);
                exportData.put("exportTime", LocalDateTime.now().toString());
                content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportData);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("导出失败: " + e.getMessage());
            }
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(session.getTitle()).append("\n\n");
            sb.append("创建时间: ").append(session.getCreateTime()).append("\n");
            sb.append("消息数: ").append(session.getMessageCount()).append("\n\n");
            sb.append("---\n\n");
            
            for (ChatHistory history : historyList) {
                sb.append("## 用户\n").append(history.getQuestion()).append("\n\n");
                sb.append("## 助手\n").append(history.getAnswer()).append("\n\n");
                sb.append("---\n\n");
            }
            content = sb.toString();
        }

        return content.getBytes();
    }

    @Override
    public String generateSessionTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.isEmpty()) {
            return "新对话";
        }
        String title = firstMessage.length() > 30 ? firstMessage.substring(0, 30) + "..." : firstMessage;
        return title.replaceAll("[\\r\\n]", " ").trim();
    }

    private String generateShareCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
