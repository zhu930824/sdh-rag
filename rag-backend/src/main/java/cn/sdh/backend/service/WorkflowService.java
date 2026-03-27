package cn.sdh.backend.service;

import cn.sdh.backend.entity.Workflow;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface WorkflowService {

    IPage<Workflow> getPage(Integer page, Integer pageSize, String keyword);

    Workflow getById(Long id);

    void save(Workflow workflow);

    void update(Workflow workflow);

    void deleteById(Long id);

    void toggleStatus(Long id);
}