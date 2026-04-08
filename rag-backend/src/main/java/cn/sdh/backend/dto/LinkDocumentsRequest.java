package cn.sdh.backend.dto;

import lombok.Data;

import java.util.List;

/**
 * 关联文档请求DTO
 */
@Data
public class LinkDocumentsRequest {

    /**
     * 要关联的文档ID列表（简化模式）
     */
    private List<Long> documentIds;

    /**
     * 文档关联配置列表（高级模式）
     * 如果提供此字段，则忽略 documentIds
     */
    private List<DocumentLinkConfig> configs;

    /**
     * 是否立即处理（可选，默认true）
     */
    private Boolean processImmediately = true;
}
