package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.KnowledgeDocumentRelation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * 知识库文档关联Mapper
 */
@Mapper
public interface KnowledgeDocumentRelationMapper extends BaseMapper<KnowledgeDocumentRelation> {

    /**
     * 根据知识库ID获取文档ID列表
     */
    @Select("SELECT document_id FROM knowledge_document_relation WHERE knowledge_id = #{knowledgeId}")
    List<Long> selectDocumentIdsByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /**
     * 根据文档ID获取知识库ID列表
     */
    @Select("SELECT knowledge_id FROM knowledge_document_relation WHERE document_id = #{documentId}")
    List<Long> selectKnowledgeIdsByDocumentId(@Param("documentId") Long documentId);

    /**
     * 删除知识库与文档的关联
     */
    @Delete("DELETE FROM knowledge_document_relation WHERE knowledge_id = #{knowledgeId} AND document_id = #{documentId}")
    int deleteByKnowledgeIdAndDocumentId(@Param("knowledgeId") Long knowledgeId, @Param("documentId") Long documentId);

    /**
     * 删除知识库的所有文档关联
     */
    @Delete("DELETE FROM knowledge_document_relation WHERE knowledge_id = #{knowledgeId}")
    int deleteByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /**
     * 获取关联记录
     */
    default KnowledgeDocumentRelation selectByKnowledgeIdAndDocumentId(Long knowledgeId, Long documentId) {
        return selectOne(new LambdaQueryWrapper<KnowledgeDocumentRelation>()
                .eq(KnowledgeDocumentRelation::getKnowledgeId, knowledgeId)
                .eq(KnowledgeDocumentRelation::getDocumentId, documentId));
    }
}