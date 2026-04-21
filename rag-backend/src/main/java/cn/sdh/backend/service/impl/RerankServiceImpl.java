package cn.sdh.backend.service.impl;

import cn.sdh.backend.service.RerankService;
import cn.sdh.backend.service.factory.RerankModelFactory;
import com.alibaba.cloud.ai.document.DocumentWithScore;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 重排序服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankServiceImpl implements RerankService {

    private final RerankModelFactory rerankModelFactory;

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topK, String rerankModel) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        log.info("重排序请求: model={}, documents={}, topK={}", rerankModel, documents.size(), topK);

        // 从工厂获取 Rerank 模型
        RerankModel model = rerankModelFactory.getModel(rerankModel);
        if (model == null) {
            log.warn("无法获取 Rerank 模型: {}", rerankModel);
            return fallbackRerank(documents, topK);
        }

        try {
            // 将字符串列表转换为 Document 列表，并在 metadata 中记录原始索引
            List<Document> docList = IntStream.range(0, documents.size())
                    .mapToObj(i -> {
                        Document doc = new Document(documents.get(i));
                        doc.getMetadata().put("original_index", i);
                        return doc;
                    })
                    .collect(Collectors.toList());

            // 创建 RerankRequest
            RerankRequest request = new RerankRequest(query, docList);

            // 执行重排序
            RerankResponse response = model.call(request);

            if (response == null || response.getResults() == null) {
                log.warn("Rerank 响应为空");
                return fallbackRerank(documents, topK);
            }

            // 转换结果
            List<RerankResult> results = response.getResults().stream()
                    .limit(topK)
                    .map(this::convertToRerankResult)
                    .collect(Collectors.toList());

            log.info("重排序完成: 输入文档数={}, 输出文档数={}", documents.size(), results.size());
            return results;

        } catch (Exception e) {
            log.error("Rerank 调用失败: {}", e.getMessage(), e);
            return fallbackRerank(documents, topK);
        }
    }

    /**
     * 转换 DocumentWithScore 为 RerankResult
     */
    private RerankResult convertToRerankResult(DocumentWithScore docWithScore) {
        RerankResult result = new RerankResult();

        String content = docWithScore.getOutput().getText();
        result.setDocument(content);
        result.setScore(docWithScore.getScore() != null ? docWithScore.getScore() : 0.0);

        // 从 metadata 中获取原始索引
        Object indexObj = docWithScore.getOutput().getMetadata().get("original_index");
        if (indexObj instanceof Number) {
            result.setIndex(((Number) indexObj).intValue());
        } else {
            result.setIndex(0);
        }

        return result;
    }

    /**
     * 降级处理：取 topK 个文档，按原始顺序返回
     */
    private List<RerankResult> fallbackRerank(List<String> documents, int topK) {
        log.warn("使用降级重排序策略");
        int limit = Math.min(topK, documents.size());
        return IntStream.range(0, limit)
                .mapToObj(i -> new RerankResult(i, documents.get(i), 0.5))
                .collect(Collectors.toList());
    }
}
