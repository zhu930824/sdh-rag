package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.KnowledgeDocumentRelation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;

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

    @Update("UPDATE knowledge_document_relation SET index_revision=index_revision+1, process_status=0, " +
            "chunk_count=0, error_message=NULL, process_time=NULL, job_id=NULL WHERE id=#{id}")
    int prepareNextIndex(@Param("id") Long id);

    @Update("UPDATE knowledge_document_relation SET chunk_mode=#{config.chunkMode}, split_type=#{config.splitType}, " +
            "chunk_size=#{config.chunkSize}, chunk_overlap=#{config.chunkOverlap}, " +
            "pages_per_chunk=#{config.pagesPerChunk}, heading_levels=#{config.headingLevels}, " +
            "regex_pattern=#{config.regexPattern}, embedding_model=#{config.embeddingModel}, " +
            "smart_config=#{config.smartConfig} WHERE id=#{id}")
    int updateIndexConfig(@Param("id") Long id,
                          @Param("config") KnowledgeDocumentRelation config);

    @Update("UPDATE knowledge_document_relation SET job_id=#{jobId} " +
            "WHERE id=#{id} AND index_revision=#{revision} AND job_id IS NULL")
    int attachIndexJob(@Param("id") Long id, @Param("revision") Integer revision,
                       @Param("jobId") Long jobId);

    @Update("UPDATE knowledge_document_relation SET process_status=1, error_message=NULL, process_time=#{now} " +
            "WHERE id=#{id} AND index_revision=#{revision} AND job_id=#{jobId}")
    int markIndexRunning(@Param("id") Long id, @Param("revision") Integer revision,
                         @Param("jobId") Long jobId, @Param("now") java.time.LocalDateTime now);

    @Update("UPDATE knowledge_document_relation SET process_status=2, chunk_count=#{chunkCount}, " +
            "error_message=NULL, process_time=#{now} WHERE id=#{id} AND index_revision=#{revision} AND job_id=#{jobId}")
    int markIndexSucceeded(@Param("id") Long id, @Param("revision") Integer revision,
                           @Param("jobId") Long jobId, @Param("chunkCount") int chunkCount,
                           @Param("now") java.time.LocalDateTime now);

    @Update("UPDATE knowledge_document_relation SET process_status=3, error_message=#{error}, process_time=#{now} " +
            "WHERE id=#{id} AND index_revision=#{revision} AND job_id=#{jobId}")
    int markIndexFailed(@Param("id") Long id, @Param("revision") Integer revision,
                        @Param("jobId") Long jobId, @Param("error") String error,
                        @Param("now") java.time.LocalDateTime now);

    @Select("SELECT * FROM knowledge_document_relation WHERE process_status=0 AND job_id IS NULL " +
            "ORDER BY id ASC LIMIT #{limit}")
    List<KnowledgeDocumentRelation> selectPendingWithoutJob(@Param("limit") int limit);

    @Select("SELECT * FROM knowledge_document_relation WHERE id=#{id} AND process_status=0 " +
            "AND job_id IS NULL FOR UPDATE")
    KnowledgeDocumentRelation selectPendingByIdForUpdate(@Param("id") Long id);
}
