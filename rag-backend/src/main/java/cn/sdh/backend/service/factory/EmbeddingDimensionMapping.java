package cn.sdh.backend.service.factory;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.TreeMap;

/**
 * Embedding 模型维度映射
 * 定义各 embedding 模型名称到向量维度的映射关系
 */
@Slf4j
public class EmbeddingDimensionMapping {

    private static final int DEFAULT_DIMENSION = 1024;

    private static final Map<String, Integer> DIMENSION_MAP = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    static {
        // DashScope (通义千问)
        DIMENSION_MAP.put("text-embedding-v3", 1024);
        DIMENSION_MAP.put("text-embedding-v2", 1536);
        DIMENSION_MAP.put("text-embedding-v1", 1536);
        DIMENSION_MAP.put("text-embedding-v4", 1536);

        // OpenAI
        DIMENSION_MAP.put("text-embedding-ada-002", 1536);
        DIMENSION_MAP.put("text-embedding-3-small", 1536);
        DIMENSION_MAP.put("text-embedding-3-large", 3072);

        // 智谱
        DIMENSION_MAP.put("embedding-3", 2048);
        DIMENSION_MAP.put("embedding-2", 1024);

        // 百川
        DIMENSION_MAP.put("baichuan-text-embedding", 1024);

        // SiliconFlow
        DIMENSION_MAP.put("BAAI/bge-large-zh-v1.5", 1024);
        DIMENSION_MAP.put("BAAI/bge-m3", 1024);

        // Ollama / 本地模型常见维度
        DIMENSION_MAP.put("nomic-embed-text", 768);
        DIMENSION_MAP.put("mxbai-embed-large", 1024);
        DIMENSION_MAP.put("all-MiniLM-L6-v2", 384);
    }

    public static int getDimension(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            log.warn("Embedding 模型名称为空，使用默认维度: {}", DEFAULT_DIMENSION);
            return DEFAULT_DIMENSION;
        }

        Integer dimension = DIMENSION_MAP.get(modelName);
        if (dimension != null) {
            return dimension;
        }

        log.warn("未知 Embedding 模型: {}，使用默认维度: {}", modelName, DEFAULT_DIMENSION);
        return DEFAULT_DIMENSION;
    }
}
