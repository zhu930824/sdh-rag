package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.EmbedConfig;
import cn.sdh.backend.mapper.EmbedConfigMapper;
import cn.sdh.backend.service.EmbedConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmbedConfigServiceImpl extends ServiceImpl<EmbedConfigMapper, EmbedConfig> implements EmbedConfigService {

    @Override
    public IPage<EmbedConfig> getPage(Integer page, Integer pageSize, String keyword) {
        Page<EmbedConfig> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<EmbedConfig> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(keyword)) {
            wrapper.like(EmbedConfig::getName, keyword);
        }
        wrapper.orderByDesc(EmbedConfig::getCreateTime);
        
        return page(pageParam, wrapper);
    }

    @Override
    public void toggleStatus(Long id) {
        EmbedConfig config = getById(id);
        if (config != null) {
            config.setStatus(config.getStatus() == 1 ? 0 : 1);
            updateById(config);
        }
    }

    @Override
    public EmbedConfig getActive() {
        return getOne(new LambdaQueryWrapper<EmbedConfig>().eq(EmbedConfig::getStatus, 1));
    }

    @Override
    public EmbedConfig getById(Long id) {
        return super.getById(id);
    }

    @Override
    public boolean save(EmbedConfig config) {
        return super.save(config);
    }

    @Override
    public void update(EmbedConfig config) {
        updateById(config);
    }

    @Override
    public void deleteById(Long id) {
        removeById(id);
    }
}