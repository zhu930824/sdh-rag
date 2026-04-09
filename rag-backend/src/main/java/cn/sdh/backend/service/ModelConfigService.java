package cn.sdh.backend.service;

import cn.sdh.backend.entity.ModelConfig;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public interface ModelConfigService {

    IPage<ModelConfig> getPage(Integer page, Integer pageSize, String keyword);

    ModelConfig getById(Long id);

    boolean save(ModelConfig config);

    void update(ModelConfig config);

    void deleteById(Long id);

    void setDefault(Long id);

    ModelConfig getDefault();

    List<ModelConfig> getActiveList();

    List<ModelConfig> getActiveChatModels();
}