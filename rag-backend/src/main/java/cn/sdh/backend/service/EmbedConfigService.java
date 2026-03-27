package cn.sdh.backend.service;

import cn.sdh.backend.entity.EmbedConfig;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface EmbedConfigService {

    IPage<EmbedConfig> getPage(Integer page, Integer pageSize, String keyword);

    EmbedConfig getById(Long id);

    void save(EmbedConfig config);

    void update(EmbedConfig config);

    void deleteById(Long id);

    void toggleStatus(Long id);

    EmbedConfig getActive();
}