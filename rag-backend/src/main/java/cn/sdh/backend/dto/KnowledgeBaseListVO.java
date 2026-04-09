package cn.sdh.backend.dto;

import cn.sdh.backend.entity.KnowledgeBase;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库列表 VO（包含文档和分块统计）
 */
@Data
public class KnowledgeBaseListVO {

    private Long id;
    private String name;
    private String description;
    private Long userId;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private String embeddingModel;
    private Integer status;
    private Boolean isPublic;
    private String icon;
    private String color;
    private String rankModel;
    private Boolean enableRewrite;
    private Double similarityThreshold;
    private Integer keywordTopK;
    private Integer vectorTopK;
    private Double keywordWeight;
    private Double vectorWeight;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 文档数量
     */
    private Integer documentCount;

    /**
     * 分块数量
     */
    private Integer chunkCount;

    /**
     * 从实体转换
     */
    public static KnowledgeBaseListVO fromEntity(KnowledgeBase entity) {
        KnowledgeBaseListVO vo = new KnowledgeBaseListVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setUserId(entity.getUserId());
        vo.setChunkSize(entity.getChunkSize());
        vo.setChunkOverlap(entity.getChunkOverlap());
        vo.setEmbeddingModel(entity.getEmbeddingModel());
        vo.setStatus(entity.getStatus());
        vo.setIsPublic(entity.getIsPublic());
        vo.setIcon(entity.getIcon());
        vo.setColor(entity.getColor());
        vo.setRankModel(entity.getRankModel());
        vo.setEnableRewrite(entity.getEnableRewrite());
        vo.setSimilarityThreshold(entity.getSimilarityThreshold());
        vo.setKeywordTopK(entity.getKeywordTopK());
        vo.setVectorTopK(entity.getVectorTopK());
        vo.setKeywordWeight(entity.getKeywordWeight());
        vo.setVectorWeight(entity.getVectorWeight());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        vo.setDocumentCount(0);
        vo.setChunkCount(0);
        return vo;
    }
}
