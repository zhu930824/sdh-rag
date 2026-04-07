package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.KnowledgeBaseTag;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库标签关联 Mapper
 */
@Mapper
public interface KnowledgeBaseTagMapper extends BaseMapper<KnowledgeBaseTag> {
}
