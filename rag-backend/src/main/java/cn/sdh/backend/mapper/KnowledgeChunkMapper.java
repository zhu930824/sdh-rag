package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.KnowledgeChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库分块 Mapper
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    /**
     * 根据文档ID查询分块列表
     */
    default List<KnowledgeChunk> selectByDocumentId(Long documentId) {
        return selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId)
                .orderByAsc(KnowledgeChunk::getChunkIndex));
    }

    /**
     * 根据文档ID和知识库ID查询分块列表
     */
    default List<KnowledgeChunk> selectByDocumentIdAndKnowledgeId(Long documentId, Long knowledgeId) {
        return selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId)
                .eq(KnowledgeChunk::getKnowledgeId, knowledgeId)
                .orderByAsc(KnowledgeChunk::getChunkIndex));
    }

    /**
     * 根据知识库ID查询分块列表（分页）
     */
    default List<KnowledgeChunk> selectByKnowledgeId(Long knowledgeId, int page, int size) {
        return selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKnowledgeId, knowledgeId)
                .orderByAsc(KnowledgeChunk::getDocumentId)
                .orderByAsc(KnowledgeChunk::getChunkIndex)
                .last("LIMIT " + size + " OFFSET " + (page - 1) * size));
    }

    /**
     * 统计知识库的分块数量
     */
    default long countByKnowledgeId(Long knowledgeId) {
        return selectCount(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKnowledgeId, knowledgeId));
    }

    /**
     * 根据文档ID删除分块
     */
    default int deleteByDocumentId(Long documentId) {
        return delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId));
    }

    /**
     * 根据文档ID和知识库ID删除分块
     */
    default int deleteByDocumentIdAndKnowledgeId(Long documentId, Long knowledgeId) {
        return delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId)
                .eq(KnowledgeChunk::getKnowledgeId, knowledgeId));
    }

    /**
     * 根据知识库ID删除所有分块
     */
    default int deleteByKnowledgeId(Long knowledgeId) {
        return delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKnowledgeId, knowledgeId));
    }
}
