package cn.sdh.backend.graph.extractor;

import cn.sdh.backend.dto.EntityExtractionResult;

/**
 * 实体提取器接口
 */
public interface EntityExtractor {

    /**
     * 从文本中提取实体和关系
     * @param text 文本内容
     * @param documentId 文档ID
     * @return 提取结果
     */
    EntityExtractionResult extract(String text, Long documentId);

    /**
     * 是否支持该文档类型
     * @param documentType 文档类型
     * @return 是否支持
     */
    default boolean supports(String documentType) {
        return true;
    }
}
