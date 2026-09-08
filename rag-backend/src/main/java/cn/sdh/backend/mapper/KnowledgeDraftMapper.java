package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.KnowledgeDraft;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeDraftMapper extends BaseMapper<KnowledgeDraft> {
    @Select("""
            SELECT DISTINCT d.* FROM knowledge_draft d
            JOIN knowledge_base kb ON kb.id = d.knowledge_id
            LEFT JOIN knowledge_base_member km ON km.knowledge_id = d.knowledge_id
                AND km.user_id = #{actor} AND km.permission = 'editor'
            WHERE (d.created_by = #{actor} OR kb.user_id = #{actor} OR km.user_id IS NOT NULL)
              AND (#{status} IS NULL OR #{status} = '' OR d.status = #{status})
            ORDER BY d.updated_at DESC LIMIT 300
            """)
    List<KnowledgeDraft> selectAccessible(@Param("actor") Long actor, @Param("status") String status);
}
