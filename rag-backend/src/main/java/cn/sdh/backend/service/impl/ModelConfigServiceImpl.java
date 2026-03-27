package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.mapper.ModelConfigMapper;
import cn.sdh.backend.service.ModelConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ModelConfigServiceImpl extends ServiceImpl<ModelConfigMapper, ModelConfig> implements ModelConfigService {

    @Override
    public IPage<ModelConfig> getPage(Integer page, Integer pageSize, String keyword) {
        Page<ModelConfig> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ModelConfig::getName, keyword)
                   .or()
                   .like(ModelConfig::getProvider, keyword);
        }
        wrapper.orderByDesc(ModelConfig::getSort);
        
        return page(pageParam, wrapper);
    }

    @Override
    public void setDefault(Long id) {
        lambdaUpdate().set(ModelConfig::getIsDefault, 0).eq(ModelConfig::getIsDefault, 1).update();
        lambdaUpdate().set(ModelConfig::getIsDefault, 1).eq(ModelConfig::getId, id).update();
    }

    @Override
    public ModelConfig getDefault() {
        return getOne(new LambdaQueryWrapper<ModelConfig>().eq(ModelConfig::getIsDefault, 1));
    }
}