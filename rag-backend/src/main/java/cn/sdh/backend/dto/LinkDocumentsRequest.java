package cn.sdh.backend.dto;

import lombok.Data;

import java.util.List;

/**
 * 关联文档请求DTO
 */
@Data
public class LinkDocumentsRequest {

    /**
     * 要关联的文档ID列表
     */
    private List<Long> documentIds;

    /**
     * 是否立即处理（可选，默认true）
     */
    private Boolean processImmediately = true;
}
