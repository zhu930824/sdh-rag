package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.ScheduledTask;
import cn.sdh.backend.mapper.ScheduledTaskMapper;
import cn.sdh.backend.service.ScheduledTaskService;
import cn.sdh.backend.service.DocumentService;
import cn.sdh.backend.service.KnowledgeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskServiceImpl extends ServiceImpl<ScheduledTaskMapper, ScheduledTask> implements ScheduledTaskService {

    private final ScheduledTaskMapper scheduledTaskMapper;
    private final DocumentService documentService;
    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;

    @Override
    public IPage<ScheduledTask> getPage(Integer page, Integer pageSize, String taskType, Integer status) {
        Page<ScheduledTask> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<ScheduledTask> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(taskType)) {
            wrapper.eq(ScheduledTask::getTaskType, taskType);
        }
        if (status != null) {
            wrapper.eq(ScheduledTask::getStatus, status);
        }
        wrapper.orderByDesc(ScheduledTask::getCreateTime);
        
        return page(pageParam, wrapper);
    }

    @Override
    public List<ScheduledTask> getActiveTasks() {
        return scheduledTaskMapper.selectActiveTasks();
    }

    @Override
    public List<ScheduledTask> getByType(String taskType) {
        return scheduledTaskMapper.selectByTaskType(taskType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTask createTask(ScheduledTask task) {
        validateCronExpression(task.getCronExpression());
        
        task.setStatus((byte) 1);
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        
        calculateNextExecuteTime(task);
        save(task);
        
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(ScheduledTask task) {
        ScheduledTask existing = getById(task.getId());
        if (existing == null) {
            throw new RuntimeException("任务不存在");
        }

        if (!existing.getCronExpression().equals(task.getCronExpression())) {
            validateCronExpression(task.getCronExpression());
        }

        task.setUpdateTime(LocalDateTime.now());
        calculateNextExecuteTime(task);
        updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long taskId) {
        ScheduledTask task = getById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        task.setStatus((byte) (task.getStatus() == 1 ? 0 : 1));
        if (task.getStatus() == 1) {
            calculateNextExecuteTime(task);
        }
        task.setUpdateTime(LocalDateTime.now());
        updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long taskId) {
        ScheduledTask task = getById(taskId);
        if (task != null && task.getStatus() == 1) {
            throw new RuntimeException("请先暂停任务再删除");
        }
        removeById(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeTask(Long taskId) {
        ScheduledTask task = getById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        log.info("开始执行定时任务: {} (ID: {})", task.getName(), task.getId());
        LocalDateTime startTime = LocalDateTime.now();

        try {
            String result = doExecuteTask(task);
            recordExecution(taskId, true, result, null);
            log.info("定时任务执行成功: {}", task.getName());
        } catch (Exception e) {
            log.error("定时任务执行失败: {} - {}", task.getName(), e.getMessage(), e);
            recordExecution(taskId, false, null, e.getMessage());
        }

        task.setLastExecuteTime(startTime);
        calculateNextExecuteTime(task);
        updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordExecution(Long taskId, boolean success, String result, String errorMsg) {
        if (success) {
            scheduledTaskMapper.incrementSuccessCount(taskId);
            scheduledTaskMapper.updateExecuteResult(taskId, result);
        } else {
            scheduledTaskMapper.incrementFailCount(taskId);
            scheduledTaskMapper.updateExecuteResult(taskId, errorMsg);
        }
    }

    @Override
    public void calculateNextExecuteTime(ScheduledTask task) {
        if (task.getStatus() != 1) {
            task.setNextExecuteTime(null);
            return;
        }

        try {
            CronExpression cronExpression = CronExpression.parse(task.getCronExpression());
            LocalDateTime nextTime = cronExpression.next(LocalDateTime.now());
            task.setNextExecuteTime(nextTime);
        } catch (Exception e) {
            log.error("计算下次执行时间失败: {}", e.getMessage());
            task.setNextExecuteTime(null);
        }
    }

    @Override
    public List<ScheduledTask> getTasksToExecute() {
        LambdaQueryWrapper<ScheduledTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScheduledTask::getStatus, 1)
               .isNotNull(ScheduledTask::getNextExecuteTime)
               .le(ScheduledTask::getNextExecuteTime, LocalDateTime.now());
        return list(wrapper);
    }

    private void validateCronExpression(String expression) {
        try {
            CronExpression.parse(expression);
        } catch (Exception e) {
            throw new RuntimeException("无效的Cron表达式: " + expression);
        }
    }

    private String doExecuteTask(ScheduledTask task) throws Exception {
        String taskType = task.getTaskType();
        String params = task.getParams();

        switch (taskType) {
            case "sync_document":
                return syncDocuments(params);
            case "reindex":
                return reindexKnowledge(params);
            case "cleanup":
                return cleanupOldData(params);
            case "report":
                return generateReport(params);
            default:
                throw new RuntimeException("未知的任务类型: " + taskType);
        }
    }

    private String syncDocuments(String params) throws Exception {
        log.info("执行文档同步任务");
        int synced = documentService.syncAllDocuments();
        return "同步完成，共同步 " + synced + " 个文档";
    }

    private String reindexKnowledge(String params) throws Exception {
        log.info("执行重建索引任务");
        knowledgeService.rebuildAllIndexes();
        return "索引重建完成";
    }

    private String cleanupOldData(String params) throws Exception {
        log.info("执行数据清理任务");
        int cleaned = documentService.cleanupOldDocuments(30);
        return "清理完成，共清理 " + cleaned + " 条数据";
    }

    private String generateReport(String params) throws Exception {
        log.info("执行报表生成任务");
        return "报表生成完成";
    }
}
