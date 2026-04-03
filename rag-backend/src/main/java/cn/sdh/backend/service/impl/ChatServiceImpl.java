package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.service.AiChatService;
import cn.sdh.backend.service.ChatService;
import cn.sdh.backend.service.ModelConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天服务实现
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private ModelConfigService modelConfigService;

    @Override
    public Flux<String> ask(String question, String sessionId, Long userId, Long knowledgeId, Long modelId) {
        log.info("用户 {} 发起问答，知识库ID: {}, 模型ID: {}, 会话ID: {}", userId, knowledgeId, modelId, sessionId);

        // 生成或使用现有的会话ID
        String currentSessionId = (sessionId != null && !sessionId.isEmpty())
                ? sessionId
                : UUID.randomUUID().toString();

        // 获取模型配置：优先使用用户指定的模型，否则使用默认模型
        String useModelId;
        if (modelId != null) {
            useModelId = String.valueOf(modelId);
        } else {
            ModelConfig defaultModel = modelConfigService.getDefault();
            useModelId = defaultModel != null ? String.valueOf(defaultModel.getId()) : null;
        }

        // 构建系统提示词
        String systemPrompt = buildSystemPrompt(knowledgeId);

        // 保存用户问题
        Long historyId = saveUserQuestion(question, currentSessionId, userId, knowledgeId);

        // 用于收集AI回复
        AtomicReference<StringBuilder> answerBuilder = new AtomicReference<>(new StringBuilder());

        // 调用AI服务
        return aiChatService.streamChat(systemPrompt, question, useModelId)
                .doOnNext(data -> {
                    // 提取内容并收集
                    if (data.contains("\"type\":\"content\"")) {
                        try {
                            int start = data.indexOf("\"content\":\"") + 11;
                            int end = data.indexOf("\"", start);
                            if (start > 10 && end > start) {
                                String content = data.substring(start, end);
                                // 反转义
                                content = content.replace("\\n", "\n")
                                        .replace("\\\"", "\"")
                                        .replace("\\\\", "\\");
                                answerBuilder.get().append(content);
                            }
                        } catch (Exception e) {
                            log.debug("解析内容失败: {}", e.getMessage());
                        }
                    }
                    // 流式响应完成，保存AI回复
                    if (data.contains("\"type\":\"done\"")) {
                        String answer = answerBuilder.get().toString();
                        saveAiAnswer(historyId, answer);
                        log.debug("会话 {} AI响应完成", currentSessionId);
                    }
                });
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(Long knowledgeId) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个智能助手，请根据用户的问题给出准确、有帮助的回答。");

        if (knowledgeId != null) {
            prompt.append(" 当前对话关联知识库ID: ").append(knowledgeId).append("。");
        }

        prompt.append("\n请用中文回答，保持回答简洁明了。");

        return prompt.toString();
    }

    /**
     * 保存用户问题
     */
    private Long saveUserQuestion(String question, String sessionId, Long userId, Long knowledgeId) {
        try {
            ChatHistory history = new ChatHistory();
            history.setSessionId(sessionId);
            history.setUserId(userId);
            history.setQuestion(question);
            history.setCreateTime(LocalDateTime.now());
            chatHistoryMapper.insert(history);
            return history.getId();
        } catch (Exception e) {
            log.error("保存用户问题失败", e);
            return null;
        }
    }

    /**
     * 保存AI回答
     */
    private void saveAiAnswer(Long historyId, String answer) {
        if (historyId == null) return;
        try {
            ChatHistory history = new ChatHistory();
            history.setId(historyId);
            history.setAnswer(answer);
            chatHistoryMapper.updateById(history);
        } catch (Exception e) {
            log.error("保存AI回答失败", e);
        }
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
        ModelConfig defaultModel = modelConfigService.getDefault();
        String modelId = defaultModel != null ? String.valueOf(defaultModel.getId()) : null;
        return aiChatService.chat(question, modelId);
    }
}
