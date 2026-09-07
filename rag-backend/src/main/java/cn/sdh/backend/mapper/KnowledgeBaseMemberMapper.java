package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.KnowledgeBaseMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface KnowledgeBaseMemberMapper extends BaseMapper<KnowledgeBaseMember> {
    @Select("SELECT permission FROM knowledge_base_member WHERE knowledge_id=#{knowledgeId} AND user_id=#{userId}")
    String permission(@Param("knowledgeId") Long knowledgeId, @Param("userId") Long userId);

    @Select("SELECT knowledge_id FROM knowledge_base_member WHERE user_id=#{userId} AND permission IN ('reader','editor')")
    List<Long> readableIds(@Param("userId") Long userId);

    @Insert("INSERT INTO knowledge_base_member (knowledge_id,user_id,permission,granted_by) VALUES (#{knowledgeId},#{userId},#{permission},#{actor}) ON DUPLICATE KEY UPDATE permission=#{permission},granted_by=#{actor},update_time=CURRENT_TIMESTAMP")
    void grant(@Param("knowledgeId") Long knowledgeId, @Param("userId") Long userId,
               @Param("permission") String permission, @Param("actor") Long actor);
}
