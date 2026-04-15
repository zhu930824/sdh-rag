package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.WorkflowExecution;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流执行记录 Mapper
 */
@Mapper
public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecution> {
}
