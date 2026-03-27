package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.DocumentProcessTask;
import cn.sdh.backend.mapper.DocumentProcessTaskMapper;
import cn.sdh.backend.service.DocumentProcessTaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DocumentProcessTaskServiceImpl extends ServiceImpl<DocumentProcessTaskMapper, DocumentProcessTask> implements DocumentProcessTaskService {

    private final DocumentProcessTaskMapper taskMapper;

    @Override
    public IPage<DocumentProcessTask> getPage(Integer page, Integer pageSize, Long documentId, String taskType, Integer status) {
        Page<DocumentProcessTask> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<DocumentProcessTask> wrapper = new LambdaQueryWrapper<>();
        
        if (documentId != null) {
            wrapper.eq(DocumentProcessTask::getDocumentId, documentId);
        }
        if (taskType != null && !taskType.isEmpty()) {
            wrapper.eq(DocumentProcessTask::getTaskType, taskType);
        }
        if (status != null) {
            wrapper.eq(DocumentProcessTask::getStatus, status);
        }
        wrapper.orderByDesc(DocumentProcessTask::getCreateTime);
        
        return page(pageParam, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentProcessTask createTask(Long documentId, String taskType) {
        DocumentProcessTask task = new DocumentProcessTask();
        task.setDocumentId(documentId);
        task.setTaskType(taskType);
        task.setStatus(0);
        task.setProgress(0);
        task.setCreateTime(LocalDateTime.now());
        save(task);

        // TODO: 异步执行处理任务
        
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryTask(Long taskId) {
        DocumentProcessTask task = getById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        task.setStatus(0);
        task.setProgress(0);
        task.setErrorMsg(null);
        task.setStartTime(null);
        task.setEndTime(null);
        updateById(task);

        // TODO: 异步执行处理任务
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProgress(Long taskId, int progress, int status) {
        DocumentProcessTask task = getById(taskId);
        if (task != null) {
            task.setProgress(progress);
            task.setStatus(status);
            if (status == 1 && task.getStartTime() == null) {
                task.setStartTime(LocalDateTime.now());
            }
            updateById(task);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(Long taskId, String result) {
        DocumentProcessTask task = getById(taskId);
        if (task != null) {
            task.setStatus(2);
            task.setProgress(100);
            task.setResult(result);
            task.setEndTime(LocalDateTime.now());
            updateById(task);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void failTask(Long taskId, String errorMsg) {
        DocumentProcessTask task = getById(taskId);
        if (task != null) {
            task.setStatus(3);
            task.setErrorMsg(errorMsg);
            task.setEndTime(LocalDateTime.now());
            updateById(task);
        }
    }
}