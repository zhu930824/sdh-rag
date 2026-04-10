package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.service.*;
import cn.sdh.backend.service.RagSearchService.ChatMessage;
import cn.sdh.backend.service.RagSearchService.RagSearchResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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

    @Autowired
    private SensitiveWordService sensitiveWordService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private RagSearchService ragSearchService;

    @Override
    public Flux<String> ask(String question, String sessionId, Long userId, Long knowledgeId, Long modelId) {
        log.info("用户 {} 发起问答，知识库ID: {}, 模型ID: {}, 会话ID: {}", userId, knowledgeId, modelId, sessionId);

        // 检测敏感词
        List<String> sensitiveWords = sensitiveWordService.findSensitiveWords(question);
        if (!sensitiveWords.isEmpty()) {
            log.warn("用户 {} 的问题包含敏感词: {}", userId, sensitiveWords);
            return Flux.just("{\"type\":\"error\",\"message\":\"您的问题包含敏感词，请修改后重试\"}");
        }

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

        // RAG召回：如果指定了知识库，从知识库召回相关内容
        String context = "";
        if (knowledgeId != null) {
            // 获取多轮对话历史（用于查询改写）
            List<ChatMessage> chatHistory = getRecentChatHistory(sessionId, userId);
            context = retrieveContext(question, knowledgeId, chatHistory);
        }

        // 构建系统提示词
        String systemPrompt = buildSystemPrompt(knowledgeId, context);

        // 保存用户问题
        Long historyId = saveUserQuestion(question, currentSessionId, userId, knowledgeId);

        // 用于收集AI回复
        AtomicReference<StringBuilder> answerBuilder = new AtomicReference<>(new StringBuilder());

        // 调用AI服务
        return aiChatService.streamChat(systemPrompt, question, useModelId)
                .filter(data -> !data.contains("\"type\":\"done\"")) // 过滤掉原有的done事件
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
                })
                // 在流结束后追加带 historyId 的 done 事件
                .concatWithValues("{\"type\":\"done\",\"historyId\":" + historyId + "}")
                .doOnNext(data -> {
                    // 流式响应完成，保存AI回复
                    if (data.contains("\"type\":\"done\"")) {
                        String answer = answerBuilder.get().toString();
                        saveAiAnswer(historyId, answer);
                        log.debug("会话 {} AI响应完成", currentSessionId);
                    }
                });
    }

    /**
     * 从知识库召回相关上下文（使用优化的RAG检索）
     */
    private String retrieveContext(String question, Long knowledgeId, List<ChatMessage> chatHistory) {
        try {
            KnowledgeBase knowledgeBase = knowledgeBaseService.getKnowledgeBaseById(knowledgeId);
            if (knowledgeBase == null) {
                log.warn("知识库不存在: {}", knowledgeId);
                return "";
            }

            log.info("RAG检索开始: knowledgeId={}, embeddingModel={}, rankModel={}, enableRewrite={}",
                    knowledgeId, knowledgeBase.getEmbeddingModel(), knowledgeBase.getRankModel(),
                    knowledgeBase.getEnableRewrite());

            // 使用优化的 RAG 检索服务
            RagSearchResult searchResult = ragSearchService.search(question, knowledgeBase, chatHistory);

            List<Document> documents = searchResult.getDocuments();
            if (documents == null || documents.isEmpty()) {
                log.info("知识库 {} 未召回相关内容", knowledgeId);
                return "";
            }

            // 拼接召回内容
            String context = documents.stream()
                    .map(doc -> {
                        String content = doc.getText();
                        return content;
                    })
                    .filter(text -> text != null && !text.trim().isEmpty())
                    .collect(Collectors.joining("\n\n---\n\n"));

            log.info("RAG检索完成: knowledgeId={}, 召回文档数={}, 总长度={}, 改写查询={}",
                    knowledgeId, documents.size(), context.length(), searchResult.getRewrittenQuery());

            return context;

        } catch (Exception e) {
            log.error("RAG检索失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
            return "";
        }
    }

    /**
     * 获取最近的对话历史（用于多轮对话改写）
     */
    private List<ChatMessage> getRecentChatHistory(String sessionId, Long userId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // 获取最近 5 轮对话
            Page<ChatHistory> historyPage = getHistory(sessionId, 1, 5, userId);
            List<ChatHistory> records = historyPage.getRecords();

            if (records.isEmpty()) {
                return Collections.emptyList();
            }

            // 按时间正序排列（从旧到新）
            return records.stream()
                    .sorted((a, b) -> a.getCreateTime().compareTo(b.getCreateTime()))
                    .flatMap(h -> {
                        List<ChatMessage> msgs = new java.util.ArrayList<>();
                        if (h.getQuestion() != null) {
                            msgs.add(new ChatMessage("user", h.getQuestion()));
                        }
                        if (h.getAnswer() != null) {
                            msgs.add(new ChatMessage("assistant", h.getAnswer()));
                        }
                        return msgs.stream();
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("获取对话历史失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(Long knowledgeId, String context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个智能助手，请根据用户的问题给出准确、有帮助的回答。");

        if (knowledgeId != null && context != null && !context.isEmpty()) {
            prompt.append("\n\n以下是从知识库中检索到的相关参考资料，请优先基于这些资料回答用户问题：\n\n");
            prompt.append("【参考资料】\n");
            prompt.append(context);
            prompt.append("\n\n【回答要求】\n");
            prompt.append("1. 请基于参考资料回答问题，如果参考资料中没有相关信息，请如实告知\n");
            prompt.append("2. 回答要准确、简洁、有条理\n");
            prompt.append("3. 如果需要，可以适当引用参考资料中的内容\n");
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
        // 检测敏感词
        List<String> sensitiveWords = sensitiveWordService.findSensitiveWords(question);
        if (!sensitiveWords.isEmpty()) {
            log.warn("用户 {} 的问题包含敏感词: {}", userId, sensitiveWords);
            return "您的问题包含敏感词，请修改后重试";
        }

        // 同步版本的聊天接口
        ModelConfig defaultModel = modelConfigService.getDefault();
        String modelId = defaultModel != null ? String.valueOf(defaultModel.getId()) : null;
        return aiChatService.chat(question, modelId);
    }
}
