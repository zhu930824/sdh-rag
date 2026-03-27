package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.Workflow;
import cn.sdh.backend.mapper.WorkflowMapper;
import cn.sdh.backend.service.WorkflowService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WorkflowServiceImpl extends ServiceImpl<WorkflowMapper, Workflow> implements WorkflowService {

    @Override
    public IPage<Workflow> getPage(Integer page, Integer pageSize, String keyword) {
        Page<Workflow> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Workflow> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Workflow::getName, keyword);
        }
        wrapper.orderByDesc(Workflow::getCreateTime);
        
        return page(pageParam, wrapper);
    }

    @Override
    public void toggleStatus(Long id) {
        Workflow workflow = getById(id);
        if (workflow != null) {
            workflow.setStatus(workflow.getStatus() == 1 ? 0 : 1);
            updateById(workflow);
        }
    }
}