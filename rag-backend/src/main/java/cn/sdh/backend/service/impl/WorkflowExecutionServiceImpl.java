package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.WorkflowExecution;
import cn.sdh.backend.mapper.WorkflowExecutionMapper;
import cn.sdh.backend.service.WorkflowExecutionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 工作流执行记录服务实现
 */
@Service
@RequiredArgsConstructor
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private final WorkflowExecutionMapper workflowExecutionMapper;

    @Override
    public WorkflowExecution create(Long workflowId, Long userId, String inputData) {
        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowId(workflowId);
        execution.setUserId(userId);
        execution.setExecutionId(UUID.randomUUID().toString().replace("-", ""));
        execution.setStatus(WorkflowExecution.STATUS_PENDING);
        execution.setInputData(inputData);
        execution.setDuration(0);
        execution.setTotalTokens(0);
        execution.setCreateTime(LocalDateTime.now());
        execution.setUpdateTime(LocalDateTime.now());
        workflowExecutionMapper.insert(execution);
        return execution;
    }

    @Override
    public void update(WorkflowExecution execution) {
        execution.setUpdateTime(LocalDateTime.now());
        workflowExecutionMapper.updateById(execution);
    }

    @Override
    public WorkflowExecution getById(Long id) {
        return workflowExecutionMapper.selectById(id);
    }

    @Override
    public WorkflowExecution getByExecutionId(String executionId) {
        LambdaQueryWrapper<WorkflowExecution> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowExecution::getExecutionId, executionId);
        return workflowExecutionMapper.selectOne(wrapper);
    }

    @Override
    public IPage<WorkflowExecution> getPage(Long workflowId, Integer page, Integer pageSize) {
        Page<WorkflowExecution> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<WorkflowExecution> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowExecution::getWorkflowId, workflowId);
        wrapper.orderByDesc(WorkflowExecution::getCreateTime);
        return workflowExecutionMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public IPage<WorkflowExecution> getPageByUser(Long userId, Integer page, Integer pageSize) {
        Page<WorkflowExecution> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<WorkflowExecution> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowExecution::getUserId, userId);
        wrapper.orderByDesc(WorkflowExecution::getCreateTime);
        return workflowExecutionMapper.selectPage(pageParam, wrapper);
    }
}
