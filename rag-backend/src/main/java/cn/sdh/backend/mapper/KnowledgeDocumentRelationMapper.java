package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.KnowledgeDocumentRelation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库文档关联Mapper
 */
@Mapper
public interface KnowledgeDocumentRelationMapper extends BaseMapper<KnowledgeDocumentRelation> {

    /**
     * 根据知识库ID获取文档ID列表
     */
    List<Long> selectDocumentIdsByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /**
     * 根据文档ID获取知识库ID列表
     */
    List<Long> selectKnowledgeIdsByDocumentId(@Param("documentId") Long documentId);

    /**
     * 删除知识库与文档的关联
     */
    int deleteByKnowledgeIdAndDocumentId(@Param("knowledgeId") Long knowledgeId, @Param("documentId") Long documentId);

    /**
     * 删除知识库的所有文档关联
     */
    int deleteByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
}
