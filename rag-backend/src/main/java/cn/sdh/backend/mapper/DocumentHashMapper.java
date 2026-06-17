package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.DocumentHash;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DocumentHashMapper extends BaseMapper<DocumentHash> {

    default DocumentHash findByKnowledgeAndMd5(Long knowledgeId, String contentMd5) {
        return selectOne(
            new LambdaQueryWrapper<DocumentHash>()
                .eq(DocumentHash::getKnowledgeId, knowledgeId)
                .eq(DocumentHash::getContentMd5, contentMd5)
        );
    }

    default List<DocumentHash> findByKnowledgeId(Long knowledgeId) {
        return selectList(
            new LambdaQueryWrapper<DocumentHash>()
                .eq(DocumentHash::getKnowledgeId, knowledgeId)
        );
    }
}
