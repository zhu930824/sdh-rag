package cn.sdh.backend.service;

import cn.sdh.backend.entity.ScheduledTask;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ScheduledTaskService extends IService<ScheduledTask> {

    IPage<ScheduledTask> getPage(Integer page, Integer pageSize, String taskType, Integer status);

    List<ScheduledTask> getActiveTasks();

    List<ScheduledTask> getByType(String taskType);

    ScheduledTask createTask(ScheduledTask task);

    void updateTask(ScheduledTask task);

    void toggleStatus(Long taskId);

    void deleteTask(Long taskId);

    void executeTask(Long taskId);

    void recordExecution(Long taskId, boolean success, String result, String errorMsg);

    void calculateNextExecuteTime(ScheduledTask task);

    List<ScheduledTask> getTasksToExecute();
}
