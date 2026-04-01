package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.service.ChatService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 聊天服务实现
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    @Override
    public Flux<String> ask(String question, String sessionId, Long userId, Long knowledgeId) {
        // TODO: 实现RAG检索和AI调用
        // 1. 如果 knowledgeId 不为空，则只从该知识库检索
        // 2. 如果 knowledgeId 为空，则从所有知识库检索
        // 3. 从向量数据库检索相关文档
        // 4. 构建提示词
        // 5. 调用AI模型（流式响应）
        // 6. 保存聊天记录

        log.info("用户 {} 发起问答，知识库ID: {}", userId, knowledgeId);

        // 临时实现：返回模拟响应
        String knowledgeInfo = knowledgeId != null
            ? "（从知识库 " + knowledgeId + " 中检索）"
            : "（从所有知识库中检索）";

        return Flux.just(
            "data: {\"type\":\"content\",\"content\":\"这是一个模拟的AI回答。" + knowledgeInfo + " 实际实现需要集成Spring AI和向量检索。\"}\n\n",
            "data: {\"type\":\"done\"}\n\n"
        );
    }

    @Override
    public Page<ChatHistory> getHistory(String sessionId, int page, int size, Long userId) {
        Page<ChatHistory> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(ChatHistory::getUserId, userId);

        if (sessionId != null) {
            wrapper.eq(ChatHistory::getSessionId, sessionId);
        }

        wrapper.orderByDesc(ChatHistory::getCreateTime);

        return chatHistoryMapper.selectPage(pageParam, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSession(String sessionId, Long userId) {
        LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatHistory::getSessionId, sessionId)
                .eq(ChatHistory::getUserId, userId);
        return chatHistoryMapper.delete(wrapper) >= 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveChatHistory(ChatHistory chatHistory) {
        if (chatHistory.getSessionId() == null) {
            chatHistory.setSessionId(UUID.randomUUID().toString());
        }
        chatHistory.setCreateTime(LocalDateTime.now());
        return chatHistoryMapper.insert(chatHistory) > 0;
    }

    @Override
    public String chat(Long userId, String question, String sessionId) {
        // 同步版本的聊天接口
        StringBuilder response = new StringBuilder();
        ask(question, sessionId, userId, null).blockLast();
        return response.toString();
    }
}
