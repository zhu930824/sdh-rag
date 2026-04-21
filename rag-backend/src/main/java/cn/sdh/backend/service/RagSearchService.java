package cn.sdh.backend.service;

import cn.sdh.backend.entity.KnowledgeBase;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * RAG检索服务接口
 * 整合混合检索、重排序、查询改写等功能
 */
public interface RagSearchService {

    /**
     * 智能检索（根据知识库配置进行混合检索、重排序）
     * @param query 查询文本
     * @param knowledgeId 知识库ID
     * @param chatHistory 多轮对话历史（可选，用于查询改写）
     * @return 检索结果
     */
    RagSearchResult search(String query, Long knowledgeId, List<ChatMessage> chatHistory);

    /**
     * 智能检索（使用知识库实体）
     * @param query 查询文本
     * @param knowledgeBase 知识库配置
     * @param chatHistory 多轮对话历史
     * @return 检索结果
     */
    RagSearchResult search(String query, KnowledgeBase knowledgeBase, List<ChatMessage> chatHistory);

    /**
     * 查询改写（多轮对话场景）
     * @param query 当前查询
     * @param chatHistory 对话历史
     * @param modelId 模型ID（用于调用LLM进行改写）
     * @return 改写后的查询
     */
    String rewriteQuery(String query, List<ChatMessage> chatHistory, Long modelId);

    /**
     * 查询扩展（生成多个语义相关的查询以提升召回率）
     * @param query 原始查询
     *param expansionCount 扩展查询数量
     * @return 包含原始查询在内的所有查询列表
     */
    List<String> expandQuery(String query, int expansionCount);

    /**
     * HyDE - 生成假设性文档
     * 通过 LLM 生成一个假设包含答案的文档，用于向量检索
     * @param query 用户查询
     * @param modelId 模型ID（可选，默认使用知识库配置的模型）
     * @return 假设性文档内容
     */
    String generateHypotheticalDocument(String query, String modelId);

    /**
     * 对话消息
     */
    class ChatMessage {
        private String role; // user 或 assistant
        private String content;

        public ChatMessage() {}

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    /**
     * 检索结果
     */
    class RagSearchResult {
        private List<Document> documents;
        private String rewrittenQuery; // 改写后的查询（如果有）
        private List<String> expandedQueries; // 扩展查询列表（如果有）
        private String hydeDocument; // HyDE 生成的假设性文档（如果有）
        private int vectorCount; // 向量检索数量
        private int keywordCount; // 关键字检索数量
        private int rerankedCount; // 重排序后数量

        public List<Document> getDocuments() {
            return documents;
        }

        public void setDocuments(List<Document> documents) {
            this.documents = documents;
        }

        public String getRewrittenQuery() {
            return rewrittenQuery;
        }

        public void setRewrittenQuery(String rewrittenQuery) {
            this.rewrittenQuery = rewrittenQuery;
        }

        public List<String> getExpandedQueries() {
            return expandedQueries;
        }

        public void setExpandedQueries(List<String> expandedQueries) {
            this.expandedQueries = expandedQueries;
        }

        public String getHydeDocument() {
            return hydeDocument;
        }

        public void setHydeDocument(String hydeDocument) {
            this.hydeDocument = hydeDocument;
        }

        public int getVectorCount() {
            return vectorCount;
        }

        public void setVectorCount(int vectorCount) {
            this.vectorCount = vectorCount;
        }

        public int getKeywordCount() {
            return keywordCount;
        }

        public void setKeywordCount(int keywordCount) {
            this.keywordCount = keywordCount;
        }

        public int getRerankedCount() {
            return rerankedCount;
        }

        public void setRerankedCount(int rerankedCount) {
            this.rerankedCount = rerankedCount;
        }
    }
}
