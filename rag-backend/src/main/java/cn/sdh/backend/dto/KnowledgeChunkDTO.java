package cn.sdh.backend.dto;

import lombok.Data;

/**
 * 分块详情DTO
 */
@Data
public class KnowledgeChunkDTO {

    private Long id;

    private Long documentId;

    private String documentTitle;

    private Integer chunkIndex;

    private String content;

    private Integer chunkSize;

    private String vectorId;

    private String createTime;

    /**
     * 内容预览（前200字符）
     */
    private String contentPreview;
}
