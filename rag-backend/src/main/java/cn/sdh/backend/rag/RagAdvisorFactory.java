package cn.sdh.backend.rag;

import cn.sdh.backend.config.RagConfig;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.service.ChatService;
import cn.sdh.backend.service.RerankService;
import cn.sdh.backend.service.VectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG Advisor 工厂
 * 根据知识库配置创建 RetrievalAugmentationAdvisor
 */
@Slf4j
@Component
public class RagAdvisorFactory {

    private final VectorStoreService vectorStoreService;
    private final RerankService rerankService;
    private final RagConfig ragConfig;
    private final ChatService chatService;

    public RagAdvisorFactory(
            VectorStoreService vectorStoreService,
            RerankService rerankService,
            RagConfig ragConfig,
            @Lazy ChatService chatService) {
        this.vectorStoreService = vectorStoreService;
        this.rerankService = rerankService;
        this.ragConfig = ragConfig;
        this.chatService = chatService;
    }

    /**
     * 创建 RAG Advisor
     *
     * @param knowledgeBase 知识库配置
     * @return RetrievalAugmentationAdvisor
     */
    public RetrievalAugmentationAdvisor createAdvisor(KnowledgeBase knowledgeBase) {
        log.info("创建 RAG Advisor: knowledgeId={}, embeddingModel={}, rankModel={}, enableRewrite={}",
                knowledgeBase.getId(),
                knowledgeBase.getEmbeddingModel(),
                knowledgeBase.getRankModel(),
                knowledgeBase.getEnableRewrite());

        // 构建各个组件
        HybridDocumentRetriever documentRetriever = new HybridDocumentRetriever(vectorStoreService, knowledgeBase);
        RerankDocumentPostProcessor postProcessor = new RerankDocumentPostProcessor(rerankService, knowledgeBase);
        HistoryAwareQueryTransformer queryTransformer = new HistoryAwareQueryTransformer(chatService, knowledgeBase);

        // 构建 QueryAugmenter（将检索到的文档注入 Prompt）
        ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .promptTemplate(createPromptTemplate(ragConfig.getRagSystemPrompt()))
                .emptyContextPromptTemplate(createPromptTemplate(ragConfig.getSystemPrompt()))
                .allowEmptyContext(true)
                .build();

        // 构建 RetrievalAugmentationAdvisor
        return RetrievalAugmentationAdvisor.builder()
                // 查询转换器（查询改写）
                .queryTransformers(queryTransformer)
                // 文档检索器（混合检索）
                .documentRetriever(documentRetriever)
                // 文档后处理器（重排序）
                .documentPostProcessors(postProcessor)
                // 查询增强器（将文档注入 Prompt）
                .queryAugmenter(queryAugmenter)
                .build();
    }

    /**
     * 创建不带文档后处理的简单 RAG Advisor（无重排序）
     */
    public RetrievalAugmentationAdvisor createSimpleAdvisor(KnowledgeBase knowledgeBase) {
        log.info("创建简单 RAG Advisor: knowledgeId={}, enableRewrite={}", knowledgeBase.getId(), knowledgeBase.getEnableRewrite());

        HybridDocumentRetriever documentRetriever = new HybridDocumentRetriever(vectorStoreService, knowledgeBase);
        HistoryAwareQueryTransformer queryTransformer = new HistoryAwareQueryTransformer(chatService, knowledgeBase);

        ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .promptTemplate(createPromptTemplate(ragConfig.getRagSystemPrompt()))
                .emptyContextPromptTemplate(createPromptTemplate(ragConfig.getSystemPrompt()))
                .allowEmptyContext(true)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(queryTransformer)
                .documentRetriever(documentRetriever)
                .queryAugmenter(queryAugmenter)
                .build();
    }

    /**
     * 创建 PromptTemplate，处理占位符
     * ContextualQueryAugmenter 需要模板中包含 {query} 和 {context} 占位符
     */
    private PromptTemplate createPromptTemplate(String template) {
        // 替换 {context} 为实际占位符格式
        // ContextualQueryAugmenter 使用 {context} 作为文档上下文，{query} 作为用户问题
        return new PromptTemplate(template);
    }
}