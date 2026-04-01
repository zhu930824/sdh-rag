package cn.sdh.backend.service;

/**
 * 向量嵌入服务接口
 */
public interface EmbeddingService {

    /**
     * 获取文本的向量嵌入
     * @param text 文本内容
     * @return 向量数组
     */
    float[] getEmbedding(String text);

    /**
     * 批量获取文本的向量嵌入
     * @param texts 文本列表
     * @return 向量数组列表
     */
    java.util.List<float[]> batchGetEmbedding(java.util.List<String> texts);

    /**
     * 计算两个文本的相似度
     * @param text1 文本1
     * @param text2 文本2
     * @return 相似度分数 (0-1)
     */
    double calculateSimilarity(String text1, String text2);
}