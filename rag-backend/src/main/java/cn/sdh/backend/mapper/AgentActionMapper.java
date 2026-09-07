package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.AgentAction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AgentActionMapper extends BaseMapper<AgentAction> {
    @Update("UPDATE agent_action SET status = #{next}, update_time = NOW() " +
            "WHERE id = #{id} AND status = #{expected}")
    int transition(@Param("id") Long id, @Param("expected") String expected, @Param("next") String next);

    @Update("UPDATE agent_action SET status = 'READY', decided_at = NOW(), update_time = NOW() " +
            "WHERE id = #{id} AND approver_id = #{actor} AND status = 'PENDING_APPROVAL'")
    int approve(@Param("id") Long id, @Param("actor") Long actor);

    @Update("UPDATE agent_action SET status = 'REJECTED', error_message = #{reason}, " +
            "decided_at = NOW(), update_time = NOW() " +
            "WHERE id = #{id} AND approver_id = #{actor} AND status = 'PENDING_APPROVAL'")
    int reject(@Param("id") Long id, @Param("actor") Long actor, @Param("reason") String reason);
}
