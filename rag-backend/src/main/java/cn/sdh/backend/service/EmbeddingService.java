package cn.sdh.backend.service;

/**
 * 向量嵌入服务接口
 */
public interface EmbeddingService {

    /**
     * 获取文本的向量嵌入（使用默认模型）
     * @param text 文本内容
     * @return 向量数组
     */
    float[] getEmbedding(String text);

    /**
     * 根据知识库ID获取文本的向量嵌入
     * @param text 文本内容
     * @param knowledgeId 知识库ID（用于选择对应的嵌入模型）
     * @return 向量数组
     */
    float[] getEmbedding(String text, Long knowledgeId);

    /**
     * 根据嵌入模型名称获取文本的向量嵌入
     * @param text 文本内容
     * @param embeddingModelName 嵌入模型名称
     * @return 向量数组
     */
    float[] getEmbeddingByModel(String text, String embeddingModelName);

    /**
     * 批量获取文本的向量嵌入
     * @param texts 文本列表
     * @return 向量数组列表
     */
    java.util.List<float[]> batchGetEmbedding(java.util.List<String> texts);

    /**
     * 根据知识库ID批量获取文本的向量嵌入
     * @param texts 文本列表
     * @param knowledgeId 知识库ID
     * @return 向量数组列表
     */
    java.util.List<float[]> batchGetEmbedding(java.util.List<String> texts, Long knowledgeId);

    /**
     * 计算两个文本的相似度
     * @param text1 文本1
     * @param text2 文本2
     * @return 相似度分数 (0-1)
     */
    double calculateSimilarity(String text1, String text2);
}