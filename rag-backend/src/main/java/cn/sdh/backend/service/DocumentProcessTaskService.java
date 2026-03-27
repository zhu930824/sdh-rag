package cn.sdh.backend.service;

import cn.sdh.backend.entity.DocumentProcessTask;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

public interface DocumentProcessTaskService extends IService<DocumentProcessTask> {

    IPage<DocumentProcessTask> getPage(Integer page, Integer pageSize, Long documentId, String taskType, Integer status);

    DocumentProcessTask createTask(Long documentId, String taskType);

    void retryTask(Long taskId);

    void updateProgress(Long taskId, int progress, int status);

    void completeTask(Long taskId, String result);

    void failTask(Long taskId, String errorMsg);
}