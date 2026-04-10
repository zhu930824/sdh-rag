package cn.sdh.backend.rag;

import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MySQL 对话记忆存储
 * 将对话记忆持久化到 MySQL 数据库
 *
 * 注意：此实现只负责读取对话历史，不负责保存
 * 保存由 ChatServiceImpl 中的 saveUserQuestion/saveAiAnswer 处理
 * 因为那些方法有 userId 等额外上下文信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MySQLChatMemoryRepository implements ChatMemoryRepository {

    private final ChatHistoryMapper chatHistoryMapper;

    @Override
    public List<String> findConversationIds() {
        // 查询所有不同的会话ID
        List<ChatHistory> histories = chatHistoryMapper.selectList(
                new LambdaQueryWrapper<ChatHistory>()
                        .select(ChatHistory::getSessionId)
                        .groupBy(ChatHistory::getSessionId)
        );
        return histories.stream()
                .map(ChatHistory::getSessionId)
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询该会话的所有历史
        List<ChatHistory> histories = chatHistoryMapper.selectList(
                new LambdaQueryWrapper<ChatHistory>()
                        .eq(ChatHistory::getSessionId, conversationId)
                        .orderByAsc(ChatHistory::getCreateTime)
        );

        // 转换为 Message 列表
        List<Message> messages = new ArrayList<>();
        for (ChatHistory h : histories) {
            if (h.getQuestion() != null) {
                messages.add(new UserMessage(h.getQuestion()));
            }
            if (h.getAnswer() != null) {
                messages.add(new AssistantMessage(h.getAnswer()));
            }
        }

        log.debug("加载会话记忆: conversationId={}, 消息数={}", conversationId, messages.size());
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // 不在此处保存消息，因为缺少 userId 等必要信息
        // 消息保存由 ChatServiceImpl 的 saveUserQuestion/saveAiAnswer 处理
        log.debug("MySQLChatMemoryRepository.saveAll 被调用，但由 ChatServiceImpl 负责实际保存");
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) {
            return;
        }

        chatHistoryMapper.delete(
                new LambdaQueryWrapper<ChatHistory>()
                        .eq(ChatHistory::getSessionId, conversationId)
        );

        log.debug("删除会话记忆: conversationId={}", conversationId);
    }
}
