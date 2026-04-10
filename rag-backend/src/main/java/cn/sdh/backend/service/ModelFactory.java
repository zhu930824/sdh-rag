package cn.sdh.backend.service;

import cn.sdh.backend.entity.ModelConfig;
import com.alibaba.cloud.ai.model.RerankModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 模型工厂接口
 * 统一管理 Chat、Embedding、Rerank 模型的创建
 */
public interface ModelFactory {

    /**
     * 根据模型名称获取 Chat 模型
     * @param modelName 模型名称（如 qwen-turbo, gpt-4 等）
     * @return ChatModel
     */
    ChatModel getChatModel(String modelName);

    /**
     * 根据模型配置 ID 获取 Chat 模型
     * @param modelId 模型配置 ID
     * @return ChatModel
     */
    ChatModel getChatModelById(Long modelId);

    /**
     * 根据模型名称获取 Embedding 模型
     * @param modelName 模型名称（如 text-embedding-v3, text-embedding-ada-002 等）
     * @return EmbeddingModel
     */
    EmbeddingModel getEmbeddingModel(String modelName);

    /**
     * 根据模型配置 ID 获取 Embedding 模型
     * @param modelId 模型配置 ID
     * @return EmbeddingModel
     */
    EmbeddingModel getEmbeddingModelById(Long modelId);

    /**
     * 根据模型名称获取 Rerank 模型
     * @param modelName 模型名称（如 qwen3-rerank, bge-reranker-v2-m3 等）
     * @return RerankModel
     */
    RerankModel getRerankModel(String modelName);

    /**
     * 根据模型配置 ID 获取 Rerank 模型
     * @param modelId 模型配置 ID
     * @return RerankModel
     */
    RerankModel getRerankModelById(Long modelId);

    /**
     * 根据模型名称获取模型配置
     * @param modelName 模型名称
     * @param modelType 模型类型（chat/embedding/reranker）
     * @return 模型配置
     */
    ModelConfig getModelConfig(String modelName, String modelType);

    /**
     * 清除缓存
     */
    void clearCache();

    /**
     * 清除指定模型的缓存
     */
    void clearCache(String modelType, String modelName);
}