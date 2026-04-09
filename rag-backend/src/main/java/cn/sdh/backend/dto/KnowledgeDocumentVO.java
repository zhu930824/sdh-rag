package cn.sdh.backend.dto;

import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.entity.KnowledgeDocumentRelation;
import lombok.Data;

/**
 * 知识库文档详情VO（包含文档信息和关联配置）
 */
@Data
public class KnowledgeDocumentVO {

    /**
     * 文档ID
     */
    private Long id;

    /**
     * 文档名称
     */
    private String title;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 处理状态：0-待处理，1-处理中，2-成功，3-失败
     */
    private Integer processStatus;

    /**
     * 切分模式：default-默认，smart-智能切分，custom-自定义
     */
    private String chunkMode;

    /**
     * 自定义切分方式：length-按长度，page-按页，heading-按标题，regex-按正则
     */
    private String splitType;

    /**
     * 切分大小
     */
    private Integer chunkSize;

    /**
     * 切分重叠
     */
    private Integer chunkOverlap;

    /**
     * 每块页数
     */
    private Integer pagesPerChunk;

    /**
     * 标题层级
     */
    private String headingLevels;

    /**
     * 正则表达式
     */
    private String regexPattern;

    /**
     * 嵌入模型
     */
    private String embeddingModel;

    /**
     * 智能配置
     */
    private String smartConfig;

    /**
     * 分块数量
     */
    private Integer chunkCount;

    /**
     * 处理时间
     */
    private String processTime;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private String createTime;

    /**
     * 从实体转换
     */
    public static KnowledgeDocumentVO fromEntity(KnowledgeDocument document, KnowledgeDocumentRelation relation) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        vo.setId(document.getId());
        vo.setTitle(document.getTitle());
        vo.setFileType(document.getFileType());
        vo.setFileSize(document.getFileSize());
        vo.setCreateTime(document.getCreateTime() != null ? document.getCreateTime().toString() : null);

        if (relation != null) {
            vo.setProcessStatus(relation.getProcessStatus());
            vo.setChunkMode(relation.getChunkMode());
            vo.setSplitType(relation.getSplitType());
            vo.setChunkSize(relation.getChunkSize());
            vo.setChunkOverlap(relation.getChunkOverlap());
            vo.setPagesPerChunk(relation.getPagesPerChunk());
            vo.setHeadingLevels(relation.getHeadingLevels());
            vo.setRegexPattern(relation.getRegexPattern());
            vo.setEmbeddingModel(relation.getEmbeddingModel());
            vo.setSmartConfig(relation.getSmartConfig());
            vo.setChunkCount(relation.getChunkCount());
            vo.setProcessTime(relation.getProcessTime() != null ? relation.getProcessTime().toString() : null);
            vo.setErrorMessage(relation.getErrorMessage());
        }

        return vo;
    }
}
