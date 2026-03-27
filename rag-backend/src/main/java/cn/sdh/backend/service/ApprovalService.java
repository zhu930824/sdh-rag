package cn.sdh.backend.service;

import cn.sdh.backend.entity.ApprovalTask;
import cn.sdh.backend.entity.ApprovalRecord;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ApprovalService extends IService<ApprovalTask> {

    IPage<ApprovalTask> getPendingTasks(Integer page, Integer pageSize);

    IPage<ApprovalTask> getUserTasks(Long userId, Integer page, Integer pageSize);

    void approve(Long taskId, Long approverId, String comment);

    void reject(Long taskId, Long approverId, String comment);

    List<ApprovalRecord> getTaskRecords(Long taskId);

    ApprovalTask createTask(Long flowId, String businessType, Long businessId, Long applicantId);
}