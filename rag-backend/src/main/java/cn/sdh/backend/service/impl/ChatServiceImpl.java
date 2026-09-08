package cn.sdh.backend.service.impl;

import cn.sdh.backend.config.IntentRouterConfig;
import cn.sdh.backend.config.RagConfig;
import cn.sdh.backend.dto.IntentRouterResponse;
import cn.sdh.backend.dto.SearchResult;
import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.memory.core.MemoryContext;
import cn.sdh.backend.memory.orchestrator.MemoryOrchestrator;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.advisor.common.ContextSettingAdvisor;
import cn.sdh.backend.advisor.common.LoggingAdvisor;
import cn.sdh.backend.advisor.common.SensitiveWordAdvisor;
import cn.sdh.backend.advisor.common.SensitiveWordAdvisor.SensitiveWordException;
import cn.sdh.backend.advisor.common.TokenUsageAdvisor;
import cn.sdh.backend.advisor.common.HotwordAdvisor;
import cn.sdh.backend.service.*;
import cn.sdh.backend.service.RagSearchService.ChatMessage;
import cn.sdh.backend.service.RagSearchService.RagSearchResult;
import cn.sdh.backend.service.factory.ChatModelFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 聊天服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatHistoryMapper chatHistoryMapper;
    private final ChatSessionService chatSessionService;
    private final ModelConfigService modelConfigService;
    private final SensitiveWordService sensitiveWordService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final RagSearchService ragSearchService;
    private final RagConfig ragConfig;
    private final ChatMemory chatMemory;
    private final ChatModelFactory chatModelFactory;
    private final TokenUsageAdvisor tokenUsageAdvisor;
    private final HotwordAdvisor hotwordAdvisor;
    private final MemoryOrchestrator memoryOrchestrator;
    private final ObjectMapper objectMapper;
    private final KnowledgeAccessService knowledgeAccessService;
    // 智能路由相关依赖
    private final IntentRouterService intentRouterService;
    private final IntentRouterConfig intentRouterConfig;

    @Override
    public Flux<String> ask(String question, String sessionId, Long userId, Long knowledgeId, Long modelId,
                            boolean memoryEnabled, Map<String, Object> metadataFilters) {
        log.info("用户 {} 发起问答，知识库ID: {}, 模型ID: {}, 会话ID: {}, 使用Advisor模式: {}",
                userId, knowledgeId, modelId, sessionId, ragConfig.isUseAdvisor());

        ModelConfig modelConfig = modelId == null
                ? modelConfigService.getDefault()
                : modelConfigService.getById(modelId);
        if (modelConfig == null) {
            return Flux.just(errorEvent("模型配置错误，请检查模型设置"));
        }

        // 智能路由：当knowledgeId为null且路由功能启用时，进行意图判断
        if (knowledgeId == null && intentRouterConfig.isEnabled()) {
            log.info("智能路由启用，开始分析用户意图: {}", question);
            try {
                IntentRouterResponse routeResult = intentRouterService.route(question, userId);

                if (!routeResult.isNeedRetrieval()) {
                    // 不需要检索，直接对话
                    log.info("智能路由决策: 无需检索知识库，原因: {}", routeResult.getReason());
                    return askDirect(question, sessionId, userId, modelConfig.getModelId(), routeResult,
                            memoryEnabled);
                }

                // 需要检索，使用推荐的知识库
                Long routedKnowledgeId = routeResult.getTopKnowledgeBaseId();
                if (routedKnowledgeId != null) {
                    log.info("智能路由决策: 推荐知识库ID: {}, 原因: {}", routedKnowledgeId, routeResult.getReason());
                    knowledgeId = routedKnowledgeId;
                } else {
                    // 需要检索但没有推荐的知识库，降级为直接对话
                    log.info("智能路由决策: 需要检索但无推荐知识库，降级为直接对话");
                    return askDirect(question, sessionId, userId, modelConfig.getModelId(), routeResult,
                            memoryEnabled);
                }

                // 根据配置选择使用 Advisor 模式还是手动 RAG 模式，并携带路由信息
                if (ragConfig.isUseAdvisor()) {
                    return askWithAdvisor(question, sessionId, userId, knowledgeId,
                            modelConfig.getModelId(), routeResult, memoryEnabled, metadataFilters);
                } else {
                    return askManual(question, sessionId, userId, knowledgeId,
                            modelConfig.getModelId(), routeResult, memoryEnabled, metadataFilters);
                }

            } catch (Exception e) {
                log.warn("智能路由失败，降级为原有逻辑: {}", e.getMessage());
                // 路由失败，继续原有逻辑
            }
        }

        // 根据配置选择使用 Advisor 模式还是手动 RAG 模式
        if (ragConfig.isUseAdvisor()) {
            return askWithAdvisor(question, sessionId, userId, knowledgeId,
                    modelConfig.getModelId(), null, memoryEnabled, metadataFilters);
        } else {
            return askManual(question, sessionId, userId, knowledgeId,
                    modelConfig.getModelId(), null, memoryEnabled, metadataFilters);
        }
    }

    /**
     * 直接对话（不使用知识库检索）
     */
    private Flux<String> askDirect(String question, String sessionId, Long userId, String modelId,
                                   IntentRouterResponse routeResult, boolean memoryEnabled) {
        // 生成或使用现有的会话ID
        String currentSessionId = (sessionId != null && !sessionId.isEmpty())
                ? sessionId
                : UUID.randomUUID().toString();

        // 获取聊天模型
        ChatModel chatModel = getChatModel(modelId);
        if (chatModel == null) {
            log.error("无法获取聊天模型，modelId: {}", modelId);
            return Flux.just("{\"type\":\"error\",\"message\":\"模型配置错误，请检查模型设置\"}");
        }

        // 保存用户问题
        Long historyId = saveUserQuestion(question, currentSessionId, userId, null);

        // 用于收集AI回复
        AtomicReference<StringBuilder> answerBuilder = new AtomicReference<>(new StringBuilder());

        // 构建简单的系统提示词
        String systemPrompt = withMemoryContext(
                "你是一个智能助手，请根据用户的问题给出准确、有帮助的回答。请用中文回答，保持回答简洁明了。",
                userId, currentSessionId, question, memoryEnabled);

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(question)
        ));

        // 构建路由信息JSON
        String routerInfoJson = buildRouterInfoJson(routeResult);

        // 发送路由信息作为第一个消息
        Flux<String> routerInfoFlux = routerInfoJson != null
                ? Flux.just(routerInfoJson)
                : Flux.empty();

        // 流式调用 AI
        return routerInfoFlux.concatWith(
                chatModel.stream(prompt)
                        .map(this::extractContent)
                        .filter(content -> content != null && !content.isEmpty())
                        .map(content -> {
                            answerBuilder.get().append(content);
                            return "{\"type\":\"content\",\"content\":\"" + escapeJson(content) + "\"}";
                        })
                        .concatWithValues("{\"type\":\"done\",\"historyId\":" + historyId + "}")
                        .doOnNext(data -> {
                            if (data.contains("\"type\":\"done\"")) {
                                String answer = answerBuilder.get().toString();
                                saveAiAnswer(historyId, answer);
                                storeMemory(userId, currentSessionId, question, answer, memoryEnabled);
                                log.debug("会话 {} AI响应完成(直接对话)", currentSessionId);
                            }
                        })
                        .onErrorResume(e -> {
                            log.error("AI调用失败", e);
                            return Flux.just("{\"type\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
                        })
        );
    }

    /**
     * 构建路由信息JSON
     */
    private String buildRouterInfoJson(IntentRouterResponse routeResult) {
        if (routeResult == null || !intentRouterConfig.isShowRouterInfo()) {
            return null;
        }

        Map<String, Object> routerInfo = new LinkedHashMap<>();
        routerInfo.put("used", true);
        routerInfo.put("needRetrieval", routeResult.isNeedRetrieval());
        routerInfo.put("reason", routeResult.getReason());
        if (routeResult.getRecommendedBases() != null && !routeResult.getRecommendedBases().isEmpty()) {
            IntentRouterResponse.KnowledgeBaseScore selected = routeResult.getRecommendedBases().get(0);
            routerInfo.put("selectedKnowledgeBaseId", selected.getKnowledgeBaseId());
            routerInfo.put("selectedKnowledgeBaseName", selected.getKnowledgeBaseName());
            routerInfo.put("candidates", routeResult.getRecommendedBases());
        }
        return jsonEvent("routerInfo", "routerInfo", routerInfo);
    }

    /**
     * 使用 Spring AI Advisor 机制的问答
     */
    private Flux<String> askWithAdvisor(String question, String sessionId, Long userId, Long knowledgeId,
                                        String modelId, IntentRouterResponse routeResult,
                                        boolean memoryEnabled, Map<String, Object> metadataFilters) {
        // 生成或使用现有的会话ID
        String currentSessionId = (sessionId != null && !sessionId.isEmpty())
                ? sessionId
                : UUID.randomUUID().toString();

        // 获取模型配置
        ChatModel chatModel = getChatModel(modelId);
        if (chatModel == null) {
            return Flux.just(errorEvent("模型配置错误，请检查模型设置"));
        }

        RetrievalPayload retrieval = retrieveContext(
                question, knowledgeId, userId, currentSessionId, metadataFilters);
        if (retrieval.forbidden()) {
            return Flux.just(errorEvent("无权访问该知识库"));
        }
        String systemPrompt = withMemoryContext(
                buildSystemPrompt(knowledgeId, retrieval.context()),
                userId, currentSessionId, question, memoryEnabled);

        // 保存用户问题
        Long historyId = saveUserQuestion(question, currentSessionId, userId, knowledgeId);

        // 用于收集AI回复
        AtomicReference<StringBuilder> answerBuilder = new AtomicReference<>(new StringBuilder());

        // 构建 ChatClient
        ChatClient.Builder clientBuilder = ChatClient.builder(chatModel);
        ChatClient.ChatClientRequestSpec requestSpec = clientBuilder.build()
                .prompt()
                .system(systemPrompt)
                .user(question);

        // 添加敏感词检测 Advisor（最先执行）
        SensitiveWordAdvisor sensitiveWordAdvisor = SensitiveWordAdvisor.builder(sensitiveWordService)
                .order(0)  // 最高优先级
                .build();
        requestSpec = requestSpec.advisors(spec -> spec.advisors(sensitiveWordAdvisor));

        // 添加对话窗口记忆 Advisor。跨会话记忆通过 system prompt 注入。
        if (memoryEnabled) {
            MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                    .conversationId(currentSessionId)
                    .build();
            requestSpec = requestSpec.advisors(spec -> spec.advisors(memoryAdvisor));
        }

        // 添加 Token 使用量统计 Advisor
        // 利用 Spring AI 的 Usage 接口获取模型用量信息
        // 注意：必须在 HTTP 请求线程中获取 userId，因为 advisor 可能在线程池中执行
        Long currentUserId = userId;

        // 使用 ContextSettingAdvisor 传递上下文信息（解决线程切换导致 ThreadLocal 丢失的问题）
        ContextSettingAdvisor contextAdvisor = ContextSettingAdvisor.create()
                .set(TokenUsageAdvisor.USER_ID, currentUserId)
                .set(TokenUsageAdvisor.SESSION_ID, currentSessionId)
                .set(TokenUsageAdvisor.MODEL_ID, modelId)
                .set(TokenUsageAdvisor.KNOWLEDGE_ID, knowledgeId)
                .set(HotwordAdvisor.USER_ID, currentUserId)
                .set(HotwordAdvisor.SESSION_ID, currentSessionId);

        requestSpec = requestSpec.advisors(spec -> spec.advisors(contextAdvisor).advisors(tokenUsageAdvisor).advisors(hotwordAdvisor));

        // 添加日志观测 Advisor（最后执行，记录完整流程）
        LoggingAdvisor loggingAdvisor = LoggingAdvisor.builder()
                .order(10000)  // 最低优先级，确保记录完整流程
                .logRequestBody(true)
                .logResponseBody(false)  // 响应内容通常较长，默认不记录
                .maxContentLength(300)
                .build();
        requestSpec = requestSpec.advisors(spec -> spec.advisors(loggingAdvisor));

        // 构建路由信息JSON
        String routerInfoJson = buildRouterInfoJson(routeResult);
        Flux<String> routerInfoFlux = routerInfoJson != null
                ? Flux.just(routerInfoJson)
                : Flux.empty();
        Flux<String> sourcesFlux = retrieval.sourcesEvent() != null
                ? Flux.just(retrieval.sourcesEvent())
                : Flux.empty();

        // 流式调用
        return routerInfoFlux.concatWith(sourcesFlux).concatWith(
                requestSpec.stream()
                        .chatResponse()
                        .map(response -> {
                            if (response.getResult() != null) {
                                response.getResult();
                                return response.getResult().getOutput().getText();
                            }
                            return null;
                        })
                        .filter(content -> content != null && !content.isEmpty())
                        .map(content -> {
                            answerBuilder.get().append(content);
                            return "{\"type\":\"content\",\"content\":\"" + escapeJson(content) + "\"}";
                        })
                        .concatWithValues("{\"type\":\"done\",\"historyId\":" + historyId + "}")
                        .doOnNext(data -> {
                            if (data.contains("\"type\":\"done\"")) {
                                String answer = answerBuilder.get().toString();
                                saveAiAnswer(historyId, answer);
                                storeMemory(userId, currentSessionId, question, answer, memoryEnabled);
                                log.debug("会话 {} AI响应完成", currentSessionId);
                            }
                        })
                        .onErrorResume(SensitiveWordException.class, e -> {
                            log.warn("敏感词拦截: {}", e.getSensitiveWords());
                            return Flux.just("{\"type\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
                        })
                        .onErrorResume(e -> {
                            log.error("AI调用失败", e);
                            return Flux.just("{\"type\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
                        })
        );
    }

    /**
     * 使用手动 RAG 流程的问答（原有实现）
     */
    private Flux<String> askManual(String question, String sessionId, Long userId, Long knowledgeId,
                                   String modelId, IntentRouterResponse routeResult,
                                   boolean memoryEnabled, Map<String, Object> metadataFilters) {

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

        // 生成请求ID并记录开始时间
        String requestId = Long.toHexString(System.currentTimeMillis()) + "-" +
                Integer.toHexString((int) (Math.random() * 0xFFFF));
        Instant startTime = Instant.now();

        // 获取聊天模型
        ChatModel chatModel = getChatModel(modelId);
        if (chatModel == null) {
            log.error("无法获取聊天模型，modelId: {}", modelId);
            return Flux.just("{\"type\":\"error\",\"message\":\"模型配置错误，请检查模型设置\"}");
        }

        // RAG召回：如果指定了知识库，从知识库召回相关内容
        RetrievalPayload retrieval = retrieveContext(
                question, knowledgeId, userId, currentSessionId, metadataFilters);
        if (retrieval.forbidden()) {
            return Flux.just(errorEvent("无权访问该知识库"));
        }

        // 构建系统提示词
        String systemPrompt = withMemoryContext(
                buildSystemPrompt(knowledgeId, retrieval.context()),
                userId, currentSessionId, question, memoryEnabled);

        // 记录请求信息
        log.info("[{}] 聊天请求开始(手动RAG) - 用户问题: {}", requestId,
                question.length() > 200 ? question.substring(0, 200) + "..." : question);

        // 保存用户问题
        Long historyId = saveUserQuestion(question, currentSessionId, userId, knowledgeId);

        // 用于收集AI回复和统计
        AtomicReference<StringBuilder> answerBuilder = new AtomicReference<>(new StringBuilder());
        AtomicInteger chunkCount = new AtomicInteger(0);

        // 构建 Prompt
        Prompt prompt;
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(question)
            ));
        } else {
            prompt = new Prompt(question);
        }

        // 构建路由信息JSON
        String routerInfoJson = buildRouterInfoJson(routeResult);
        Flux<String> routerInfoFlux = routerInfoJson != null
                ? Flux.just(routerInfoJson)
                : Flux.empty();
        Flux<String> sourcesFlux = retrieval.sourcesEvent() != null
                ? Flux.just(retrieval.sourcesEvent())
                : Flux.empty();

        // 流式调用 AI
        return routerInfoFlux.concatWith(sourcesFlux).concatWith(
                chatModel.stream(prompt)
                        .map(this::extractContent)
                        .filter(content -> content != null && !content.isEmpty())
                        .map(content -> {
                            answerBuilder.get().append(content);
                            chunkCount.incrementAndGet();
                            return "{\"type\":\"content\",\"content\":\"" + escapeJson(content) + "\"}";
                        })
                        .concatWithValues("{\"type\":\"done\",\"historyId\":" + historyId + "}")
                        .doOnNext(data -> {
                            if (data.contains("\"type\":\"done\"")) {
                                String answer = answerBuilder.get().toString();
                                saveAiAnswer(historyId, answer);
                                storeMemory(userId, currentSessionId, question, answer, memoryEnabled);

                                // 记录完成日志
                                Duration duration = Duration.between(startTime, Instant.now());
                                log.info("[{}] 聊天请求完成(手动RAG) - 耗时: {}ms, chunks: {}, 内容长度: {}",
                                        requestId, duration.toMillis(), chunkCount.get(), answer.length());
                            }
                        })
                        .onErrorResume(e -> {
                            Duration duration = Duration.between(startTime, Instant.now());
                            log.error("[{}] 聊天请求失败(手动RAG) - 耗时: {}ms, 错误: {}",
                                    requestId, duration.toMillis(), e.getMessage());
                            return Flux.just("{\"type\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
                        })
        );
    }

    /**
     * 从响应中提取内容
     */
    private String extractContent(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * 从知识库召回相关上下文（使用优化的RAG检索）
     */
    private RetrievalPayload retrieveContext(String question, Long knowledgeId, Long userId,
                                             String sessionId, Map<String, Object> metadataFilters) {
        if (knowledgeId == null) {
            return RetrievalPayload.empty();
        }
        try {
            KnowledgeBase knowledgeBase = knowledgeBaseService.getKnowledgeBaseById(knowledgeId);
            if (knowledgeBase == null) {
                log.warn("知识库不存在: {}", knowledgeId);
                return RetrievalPayload.empty();
            }
            if (!canAccessKnowledgeBase(knowledgeBase, userId)) {
                log.warn("用户 {} 尝试访问无权限知识库 {}", userId, knowledgeId);
                return RetrievalPayload.forbiddenPayload();
            }

            log.info("级联RAG检索开始: knowledgeId={}, embeddingModel={}, rankModel={}, enableRewrite={}, filters={}",
                    knowledgeId, knowledgeBase.getEmbeddingModel(), knowledgeBase.getRankModel(),
                    knowledgeBase.getEnableRewrite(), metadataFilters);

            List<ChatMessage> chatHistory = getRecentChatHistory(sessionId, userId);
            SearchResult searchResult = ragSearchService.searchWithCascade(
                    question, knowledgeBase, chatHistory,
                    metadataFilters == null ? Map.of() : metadataFilters);

            if (searchResult == null || searchResult.getChunks() == null
                    || searchResult.getChunks().isEmpty()) {
                log.info("知识库 {} 未召回相关内容", knowledgeId);
                return RetrievalPayload.empty();
            }

            String context = buildHierarchicalContext(searchResult);
            String sourcesEvent = buildSourcesEvent(searchResult.getChunks());
            List<Map<String, Object>> sources = buildSourcesList(searchResult.getChunks());
            String sourcesPayloadJson = sources == null ? null : writeJson(sources);

            log.info("级联RAG检索完成: knowledgeId={}, 召回片段数={}, 总长度={}",
                    knowledgeId, searchResult.getChunks().size(), context.length());

            return new RetrievalPayload(context, sourcesEvent, sourcesPayloadJson, false);

        } catch (Exception e) {
            log.error("RAG检索失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
            return RetrievalPayload.empty();
        }
    }

    private boolean canAccessKnowledgeBase(KnowledgeBase knowledgeBase, Long userId) {
        return knowledgeAccessService.canRead(knowledgeBase, userId);
    }

    private String buildSourcesEvent(List<SearchResult.RankedChunk> chunks) {
        List<Map<String, Object>> sources = buildSourcesList(chunks);
        return sources == null ? null : jsonEvent("sources", "sources", sources);
    }

    /** 来源列表(与 sources 事件同构,单独抽出供落库回放) */
    private List<Map<String, Object>> buildSourcesList(List<SearchResult.RankedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }
        Map<String, SearchResult.RankedChunk> topChunkByDocument = new LinkedHashMap<>();
        for (SearchResult.RankedChunk chunk : chunks) {
            String key = chunk.getDocumentId() != null
                    ? chunk.getDocumentId().toString()
                    : String.valueOf(chunk.getDocumentTitle());
            topChunkByDocument.putIfAbsent(key, chunk);
        }
        return topChunkByDocument.values().stream().map(chunk -> {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("documentId", chunk.getDocumentId());
            source.put("documentTitle", chunk.getDocumentTitle());
            source.put("content", chunk.getContent());
            source.put("score", chunk.getFinalScore() != null
                    ? chunk.getFinalScore()
                    : chunk.getRelevanceScore());
            source.put("chunkIndex", chunk.getChunkIndex());
            source.put("totalChunks", chunk.getTotalChunks());
            return source;
        }).toList();
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
                        List<ChatMessage> msgs = new ArrayList<>();
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
    /**
     * Build hierarchical document context from ranked chunks (RAG optimization)
     */
    private String buildHierarchicalContext(SearchResult result) {
        if (result == null || result.getChunks() == null || result.getChunks().isEmpty()) return "";

        Map<Long, List<SearchResult.RankedChunk>> groupedByDoc = new LinkedHashMap<>();
        for (SearchResult.RankedChunk chunk : result.getChunks()) {
            groupedByDoc.computeIfAbsent(chunk.getDocumentId(), k -> new ArrayList<>()).add(chunk);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[Retrieval Context]\n\n");
        for (var entry : groupedByDoc.entrySet()) {
            SearchResult.RankedChunk first = entry.getValue().get(0);
            String docDate = first.getDocDate() != null ? first.getDocDate().toString() : "unknown date";
            String department = first.getDepartment() != null ? first.getDepartment() : "unknown dept";
            String docType = first.getDocType() != null ? first.getDocType() : "unknown type";

            sb.append(String.format("Document: %s (%s / %s / %s)\n",
                first.getDocumentTitle(), department, docType, docDate));
            if (first.getDocumentSummary() != null && !first.getDocumentSummary().isEmpty()) {
                sb.append("Summary: ").append(first.getDocumentSummary()).append("\n");
            }
            for (SearchResult.RankedChunk chunk : entry.getValue()) {
                double score = chunk.getFinalScore() != null ? chunk.getFinalScore() :
                    (chunk.getRelevanceScore() != null ? chunk.getRelevanceScore() : 0);
                sb.append(String.format("  [Chunk %d/%d] (score: %.2f):\n  %s\n",
                    chunk.getChunkIndex() != null ? chunk.getChunkIndex() : 0,
                    chunk.getTotalChunks() != null ? chunk.getTotalChunks() : 0,
                    score,
                    chunk.getContent() != null ? chunk.getContent().trim() : ""));
            }
            sb.append("\n---\n\n");
        }
        return sb.toString();
    }

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

    private String withMemoryContext(String basePrompt, Long userId, String sessionId,
                                     String question, boolean memoryEnabled) {
        if (!memoryEnabled) {
            return basePrompt;
        }
        try {
            MemoryContext memoryContext = memoryOrchestrator.assembleContext(userId, sessionId, question);
            if (memoryContext != null && memoryContext.hasMemory()) {
                return basePrompt + "\n\n【用户记忆上下文】\n" + memoryContext.buildSystemPrompt();
            }
        } catch (Exception e) {
            log.warn("读取用户记忆失败，继续无记忆问答: userId={}, error={}", userId, e.getMessage());
        }
        return basePrompt;
    }

    private void storeMemory(Long userId, String sessionId, String question,
                             String answer, boolean memoryEnabled) {
        if (!memoryEnabled || answer == null || answer.isBlank()) {
            return;
        }
        try {
            memoryOrchestrator.processAndStore(userId, sessionId, question, answer);
        } catch (Exception e) {
            log.warn("写入用户记忆失败，不影响本次回答: userId={}, error={}", userId, e.getMessage());
        }
    }

    private String jsonEvent(String type, String payloadName, Object payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put(payloadName, payload);
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("序列化流式事件失败: type={}", type, e);
            return "{\"type\":\"error\",\"message\":\"流式事件序列化失败\"}";
        }
    }

    private String errorEvent(String message) {
        return jsonEvent("error", "message", message);
    }

    private record RetrievalPayload(String context, String sourcesEvent, String sourcesPayloadJson,
                                    boolean forbidden) {
        private static RetrievalPayload empty() {
            return new RetrievalPayload("", null, null, false);
        }

        private static RetrievalPayload forbiddenPayload() {
            return new RetrievalPayload("", null, null, true);
        }
    }

    /** 序列化辅助(失败返回 null,不影响主流程) */
    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return null;
        }
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
            history.setKnowledgeId(knowledgeId);
            history.setCreateTime(LocalDateTime.now());
            chatHistoryMapper.insert(history);
            ensureSessionRow(sessionId, userId, question, null);
            return history.getId();
        } catch (Exception e) {
            log.error("保存用户问题失败", e);
            return null;
        }
    }

    /** 会话档案自动建档(chat_session 行;与 /api/chat/session 页面共用同一数据源) */
    private void ensureSessionRow(String sessionId, Long userId, String firstQuestion, Long modelId) {
        try {
            if (sessionId == null || userId == null) return;
            chatSessionService.touch(sessionId, userId, firstQuestion, modelId);
        } catch (Exception e) {
            log.warn("会话档案建档失败,不影响问答: {}", e.getMessage());
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
            history.setUpdateTime(LocalDateTime.now());
            chatHistoryMapper.updateById(history);
        } catch (Exception e) {
            log.error("保存AI回答失败", e);
        }
    }

    // ===================== Agent 复用接缝(供 cn.sdh.backend.agent 调用,不改动既有问答流程) =====================

    /**
     * Agent 复用:级联检索,返回证据文本 / sources 事件 / sources payload JSON 三者(各自可空)。
     * forbidden=true 表示无权访问该知识库。
     */
    public AgentRetrieval retrieveForAgent(String question, Long knowledgeId, Long userId,
                                           String sessionId, Map<String, Object> metadataFilters) {
        RetrievalPayload payload = retrieveContext(question, knowledgeId, userId, sessionId, metadataFilters);
        return new AgentRetrieval(payload.context(), payload.sourcesEvent(), payload.sourcesPayloadJson(),
                payload.forbidden());
    }

    public record AgentRetrieval(String context, String sourcesEvent, String sourcesPayloadJson,
                                 boolean forbidden) {
    }

    /** Agent 复用:注入记忆上下文段(带异常兜底,与普通问答一致) */
    public String memoryPrompt(String basePrompt, Long userId, String sessionId,
                               String question, boolean memoryEnabled) {
        return withMemoryContext(basePrompt, userId, sessionId, question, memoryEnabled);
    }

    /** Agent 复用:完成后写入分层记忆(失败不影响主流程) */
    public void storeMemoryAfterTurn(Long userId, String sessionId, String question,
                                     String answer, boolean memoryEnabled) {
        storeMemory(userId, sessionId, question, answer, memoryEnabled);
    }

    /** Agent 复用:落库用户问题(带知识库与模型) */
    public Long saveQuestionRow(String question, String sessionId, Long userId,
                                Long knowledgeId, Long modelConfigId) {
        try {
            ChatHistory history = new ChatHistory();
            history.setSessionId(sessionId);
            history.setUserId(userId);
            history.setQuestion(question);
            history.setKnowledgeId(knowledgeId);
            history.setModelId(modelConfigId);
            history.setCreateTime(LocalDateTime.now());
            chatHistoryMapper.insert(history);
            ensureSessionRow(sessionId, userId, question, modelConfigId);
            return history.getId();
        } catch (Exception e) {
            log.error("保存用户问题失败", e);
            return null;
        }
    }

    /** Agent 复用:落库完整回答(答案 + 来源 + 路由 + Agent 运行痕迹) */
    public void persistAnswerRow(Long historyId, String answer, Long knowledgeId, Long modelConfigId,
                                 String sourcesPayloadJson, String routerInfoJson,
                                 String agentRunKey, String agentTraceJson) {
        if (historyId == null) return;
        try {
            ChatHistory history = new ChatHistory();
            history.setId(historyId);
            history.setAnswer(answer);
            history.setKnowledgeId(knowledgeId);
            history.setModelId(modelConfigId);
            history.setSources(sourcesPayloadJson);
            history.setRouterInfo(routerInfoJson);
            history.setAgentRunKey(agentRunKey);
            history.setAgentTrace(agentTraceJson);
            history.setUpdateTime(LocalDateTime.now());
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

        // 获取聊天模型
        ChatModel chatModel = getChatModel(null);
        if (chatModel == null) {
            return "模型配置错误，请检查模型设置";
        }

        try {
            Prompt prompt = new Prompt(question);
            ChatResponse response = chatModel.call(prompt);
            return extractContent(response);
        } catch (Exception e) {
            log.error("AI调用失败", e);
            return "AI调用失败: " + e.getMessage();
        }
    }

    @Override
    public String chat(String question, String modelId) {
        // 获取聊天模型
        ChatModel chatModel = getChatModel(modelId);
        if (chatModel == null) {
            log.error("无法获取聊天模型");
            return null;
        }

            try {
                Prompt prompt = new Prompt(question);
                ChatResponse response = chatModel.call(prompt);
                return extractContent(response);
            } catch (Exception e) {
                log.error("AI调用失败: {}", e.getMessage(), e);
                return null;
        }
    }

    /**
     * 获取聊天模型
     */
    private ChatModel getChatModel(String modelId) {
        // 优先使用指定的模型ID
        if (modelId != null) {
            ChatModel model = chatModelFactory.getModelById(modelId);
            if (model != null) {
                return model;
            }
        }

        // 获取默认模型配置
        ModelConfig defaultConfig = modelConfigService.getDefault();
        if (defaultConfig != null) {
            return chatModelFactory.getModelById(defaultConfig.getModelId());
        }

        // 使用默认模型名称
        return chatModelFactory.getModel(null);
    }

    /**
     * 转义JSON字符串
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
