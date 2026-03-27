package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.ApprovalTask;
import cn.sdh.backend.entity.ApprovalRecord;
import cn.sdh.backend.mapper.ApprovalTaskMapper;
import cn.sdh.backend.mapper.ApprovalRecordMapper;
import cn.sdh.backend.service.ApprovalService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApprovalServiceImpl extends ServiceImpl<ApprovalTaskMapper, ApprovalTask> implements ApprovalService {

    private final ApprovalTaskMapper approvalTaskMapper;
    private final ApprovalRecordMapper approvalRecordMapper;

    @Override
    public IPage<ApprovalTask> getPendingTasks(Integer page, Integer pageSize) {
        Page<ApprovalTask> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<ApprovalTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalTask::getStatus, 0)
               .orderByDesc(ApprovalTask::getCreateTime);
        return page(pageParam, wrapper);
    }

    @Override
    public IPage<ApprovalTask> getUserTasks(Long userId, Integer page, Integer pageSize) {
        Page<ApprovalTask> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<ApprovalTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalTask::getApplicantId, userId)
               .orderByDesc(ApprovalTask::getCreateTime);
        return page(pageParam, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long taskId, Long approverId, String comment) {
        ApprovalTask task = getById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (task.getStatus() != 0) {
            throw new RuntimeException("任务已处理");
        }

        ApprovalRecord record = new ApprovalRecord();
        record.setTaskId(taskId);
        record.setApproverId(approverId);
        record.setNodeOrder(task.getCurrentNode());
        record.setAction((byte) 1);
        record.setComment(comment);
        record.setCreateTime(LocalDateTime.now());
        approvalRecordMapper.insert(record);

        task.setStatus(1);
        task.setUpdateTime(LocalDateTime.now());
        updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long taskId, Long approverId, String comment) {
        ApprovalTask task = getById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (task.getStatus() != 0) {
            throw new RuntimeException("任务已处理");
        }

        ApprovalRecord record = new ApprovalRecord();
        record.setTaskId(taskId);
        record.setApproverId(approverId);
        record.setNodeOrder(task.getCurrentNode());
        record.setAction((byte) 2);
        record.setComment(comment);
        record.setCreateTime(LocalDateTime.now());
        approvalRecordMapper.insert(record);

        task.setStatus(2);
        task.setUpdateTime(LocalDateTime.now());
        updateById(task);
    }

    @Override
    public List<ApprovalRecord> getTaskRecords(Long taskId) {
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalRecord::getTaskId, taskId)
               .orderByAsc(ApprovalRecord::getCreateTime);
        return approvalRecordMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalTask createTask(Long flowId, String businessType, Long businessId, Long applicantId) {
        ApprovalTask task = new ApprovalTask();
        task.setFlowId(flowId);
        task.setBusinessType(businessType);
        task.setBusinessId(businessId);
        task.setApplicantId(applicantId);
        task.setCurrentNode(1);
        task.setStatus(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        save(task);
        return task;
    }
}