package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.ScheduledTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ScheduledTaskMapper extends BaseMapper<ScheduledTask> {

    @Select("SELECT * FROM scheduled_task WHERE status = 1")
    List<ScheduledTask> selectActiveTasks();

    @Select("SELECT * FROM scheduled_task WHERE task_type = #{taskType}")
    List<ScheduledTask> selectByTaskType(@Param("taskType") String taskType);

    @Update("UPDATE scheduled_task SET last_execute_time = NOW(), last_result = #{result} WHERE id = #{id}")
    void updateExecuteResult(@Param("id") Long id, @Param("result") String result);

    @Update("UPDATE scheduled_task SET success_count = success_count + 1 WHERE id = #{id}")
    void incrementSuccessCount(@Param("id") Long id);

    @Update("UPDATE scheduled_task SET fail_count = fail_count + 1 WHERE id = #{id}")
    void incrementFailCount(@Param("id") Long id);
}
