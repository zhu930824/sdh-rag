package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.AgentRun;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AgentRunMapper extends BaseMapper<AgentRun> {

    /** 状态 CAS:仅当当前状态为 expected 时置为 next,返回影响行数(0=竞态失败) */
    @Update("UPDATE agent_run SET status = #{next}, updated_at = NOW() " +
            "WHERE run_key = #{runKey} AND status = #{expected} AND lease_owner IS NULL")
    int transition(@Param("runKey") String runKey,
                   @Param("expected") String expected,
                   @Param("next") String next);

    @Update("UPDATE agent_run SET lease_owner=NULL,lease_until=NULL WHERE id=#{id} AND lease_owner=#{owner} AND execution_epoch=#{epoch}")
    int releaseLease(@Param("id") long id, @Param("owner") String owner, @Param("epoch") long epoch);

    @Update("UPDATE agent_run SET pending_action_id=#{actionId},updated_at=NOW() WHERE id=#{id} " +
            "AND status IN ('WAITING_APPROVAL','WAITING_ACTION') AND lease_owner IS NULL")
    int updatePendingAction(@Param("id") Long id, @Param("actionId") Long actionId);
}
