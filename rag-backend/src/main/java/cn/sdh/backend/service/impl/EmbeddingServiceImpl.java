package cn.sdh.backend.service.impl;

import cn.sdh.backend.service.EmbeddingModelFactory;
import cn.sdh.backend.service.EmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量嵌入服务实现
 * 支持根据知识库配置动态选择嵌入模型
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private final EmbeddingModelFactory embeddingModelFactory;
    private final ObjectMapper objectMapper;

    @Override
    public float[] getEmbedding(String text) {
        return getEmbeddingByModel(text, null);
    }

    @Override
    public float[] getEmbedding(String text, Long knowledgeId) {
        if (text == null || text.isEmpty()) {
            return new float[0];
        }

        try {
            EmbeddingModel embeddingModel = embeddingModelFactory.getEmbeddingModelByKnowledgeId(knowledgeId);
            if (embeddingModel == null) {
                log.error("无法获取嵌入模型: knowledgeId={}", knowledgeId);
                return new float[0];
            }

            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            if (response != null && !response.getResults().isEmpty()) {
                return response.getResults().get(0).getOutput();
            }
        } catch (Exception e) {
            log.error("获取文本向量失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
        }

        return new float[0];
    }

    @Override
    public float[] getEmbeddingByModel(String text, String embeddingModelName) {
        if (text == null || text.isEmpty()) {
            return new float[0];
        }

        try {
            EmbeddingModel embeddingModel = embeddingModelFactory.getEmbeddingModel(embeddingModelName);
            if (embeddingModel == null) {
                log.error("无法获取嵌入模型: modelName={}", embeddingModelName);
                return new float[0];
            }

            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            if (response != null && !response.getResults().isEmpty()) {
                return response.getResults().get(0).getOutput();
            }
        } catch (Exception e) {
            log.error("获取文本向量失败: modelName={}, error={}", embeddingModelName, e.getMessage(), e);
        }

        return new float[0];
    }

    @Override
    public List<float[]> batchGetEmbedding(List<String> texts) {
        return batchGetEmbedding(texts, null);
    }

    @Override
    public List<float[]> batchGetEmbedding(List<String> texts, Long knowledgeId) {
        List<float[]> results = new ArrayList<>();

        if (texts == null || texts.isEmpty()) {
            return results;
        }

        try {
            EmbeddingModel embeddingModel;
            if (knowledgeId != null) {
                embeddingModel = embeddingModelFactory.getEmbeddingModelByKnowledgeId(knowledgeId);
            } else {
                embeddingModel = embeddingModelFactory.getEmbeddingModel(null);
            }

            if (embeddingModel == null) {
                log.error("无法获取嵌入模型: knowledgeId={}", knowledgeId);
                return results;
            }

            EmbeddingResponse response = embeddingModel.embedForResponse(texts);
            if (response != null) {
                response.getResults().forEach(embedding -> {
                    results.add(embedding.getOutput());
                });
            }
        } catch (Exception e) {
            log.error("批量获取文本向量失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
        }

        return results;
    }

    @Override
    public double calculateSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.isEmpty() || text2.isEmpty()) {
            return 0.0;
        }

        float[] embedding1 = getEmbedding(text1);
        float[] embedding2 = getEmbedding(text2);

        return cosineSimilarity(embedding1, embedding2);
    }

    private double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1 == null || vec2 == null || vec1.length == 0 || vec2.length == 0) {
            return 0.0;
        }

        if (vec1.length != vec2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}