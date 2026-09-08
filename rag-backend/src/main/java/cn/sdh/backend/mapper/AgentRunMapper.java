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
            "WHERE run_key = #{runKey} AND status = #{expected}")
    int transition(@Param("runKey") String runKey,
                   @Param("expected") String expected,
                   @Param("next") String next);
}
