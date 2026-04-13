package cn.sdh.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG Configuration Class
 * Controls whether to use Advisor mode and related parameters
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag")
public class RagConfig {

    /**
     * Whether to use Advisor mechanism
     * true: use Spring AI RetrievalAugmentationAdvisor
     * false: use original manual RAG flow
     */
    private boolean useAdvisor = true;

    /**
     * Chat memory window size (keep recent N messages)
     */
    private int memoryWindowSize = 10;

    /**
     * Default system prompt template (used when no context is available)
     * This template is used by emptyContextPromptTemplate in ContextualQueryAugmenter
     */
    private String systemPrompt = "You are an intelligent assistant. Please provide accurate and helpful answers based on user questions.\n\nUser question: {query}\n\nPlease answer in Chinese and keep the response concise and clear.";

    /**
     * RAG system prompt template (with context)
     * This template must contain {query} and {context} placeholders for ContextualQueryAugmenter
     */
    private String ragSystemPrompt = "You are an intelligent assistant. Please provide accurate and helpful answers based on user questions.\n\nThe following are relevant reference materials retrieved from the knowledge base:\n\n{context}\n\nUser question: {query}\n\n[Answer Requirements]\n1. Please answer based on the reference materials. If there is no relevant information, please state it honestly\n2. The answer should be accurate, concise, and organized\n3. You can appropriately quote content from the reference materials if needed\n\nPlease answer in Chinese and keep the response concise and clear.";
}
