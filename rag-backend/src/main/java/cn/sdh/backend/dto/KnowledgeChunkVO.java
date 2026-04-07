package cn.sdh.backend.dto;

import cn.sdh.backend.entity.KnowledgeChunk;
import cn.sdh.backend.entity.KnowledgeDocument;
import lombok.Data;

/**
 * 分块详情VO（包含文档信息）
 */
@Data
public class KnowledgeChunkVO {

    private Long id;

    private Long documentId;

    private String documentTitle;

    private Long knowledgeId;

    private Integer chunkIndex;

    private String content;

    private Integer chunkSize;

    private String vectorId;

    private String createTime;

    /**
     * 从实体转换
     */
    public static KnowledgeChunkVO fromEntity(KnowledgeChunk chunk, KnowledgeDocument document) {
        KnowledgeChunkVO vo = new KnowledgeChunkVO();
        vo.setId(chunk.getId());
        vo.setDocumentId(chunk.getDocumentId());
        vo.setDocumentTitle(document != null ? document.getTitle() : "未知文档");
        vo.setKnowledgeId(chunk.getKnowledgeId());
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setContent(chunk.getContent());
        vo.setChunkSize(chunk.getChunkSize());
        vo.setVectorId(chunk.getVectorId());
        vo.setCreateTime(chunk.getCreateTime() != null ? chunk.getCreateTime().toString() : null);
        return vo;
    }
}
