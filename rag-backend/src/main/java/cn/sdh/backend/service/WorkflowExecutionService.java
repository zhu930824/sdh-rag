package cn.sdh.backend.service;

import cn.sdh.backend.entity.WorkflowExecution;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 工作流执行记录服务接口
 */
public interface WorkflowExecutionService {

    /**
     * 创建执行记录
     */
    WorkflowExecution create(Long workflowId, Long userId, String inputData);

    /**
     * 更新执行记录
     */
    void update(WorkflowExecution execution);

    /**
     * 状态 CAS(审批续跑竞态控制):仅 expected 状态可迁到 next
     * @return 是否迁移成功
     */
    boolean transition(Long id, String expected, String next);

    /**
     * 根据ID获取执行记录
     */
    WorkflowExecution getById(Long id);

    /**
     * 根据执行ID获取执行记录
     */
    WorkflowExecution getByExecutionId(String executionId);

    /**
     * 分页查询工作流的执行记录
     */
    IPage<WorkflowExecution> getPage(Long workflowId, Integer page, Integer pageSize);

    /**
     * 分页查询用户的执行记录
     */
    IPage<WorkflowExecution> getPageByUser(Long userId, Integer page, Integer pageSize);
}
