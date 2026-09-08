package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.WorkflowExecution;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 工作流执行记录 Mapper
 */
@Mapper
public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecution> {

    /** 状态 CAS:仅当前状态为 expected 时置为 next(审批续跑竞态控制) */
    @Update("UPDATE workflow_execution SET status = #{next}, update_time = NOW() " +
            "WHERE id = #{id} AND status = #{expected}")
    int transition(@Param("id") Long id,
                   @Param("expected") String expected,
                   @Param("next") String next);
}
