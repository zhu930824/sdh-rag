package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.DocumentChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    @Select("SELECT COUNT(*) FROM document_chunk WHERE knowledge_id = #{knowledgeId}")
    int countByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    @Select("SELECT COUNT(*) FROM document_chunk WHERE knowledge_id = #{knowledgeId} AND embedding IS NOT NULL")
    int countEmbeddingByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    @Select("SELECT COUNT(*) FROM document_chunk WHERE knowledge_id = #{knowledgeId} AND (content IS NULL OR content = '')")
    int countEmptyByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    @Select("SELECT COUNT(*) FROM document_chunk WHERE knowledge_id = #{knowledgeId}")
    long selectIndexSizeByKnowledge(@Param("knowledgeId") Long knowledgeId);

    @Select("SELECT UNIX_TIMESTAMP(MAX(update_time)) * 1000 FROM document_chunk WHERE knowledge_id = #{knowledgeId}")
    Long selectLastUpdateTimeByKnowledge(@Param("knowledgeId") Long knowledgeId);

    @Select("SELECT SUM(LENGTH(content)) FROM document_chunk")
    Long selectTotalContentLength();

    @Select("SELECT COUNT(*) FROM document_chunk")
    Long selectTotalCount();

    @Select("SELECT COUNT(*) FROM document_chunk WHERE content LIKE CONCAT('%', #{term}, '%')")
    Long selectCountContainingTerm(@Param("term") String term);
}
