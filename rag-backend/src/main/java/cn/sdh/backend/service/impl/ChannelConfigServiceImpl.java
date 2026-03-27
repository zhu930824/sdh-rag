package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.ChannelConfig;
import cn.sdh.backend.mapper.ChannelConfigMapper;
import cn.sdh.backend.service.ChannelConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChannelConfigServiceImpl extends ServiceImpl<ChannelConfigMapper, ChannelConfig> implements ChannelConfigService {

    private final ChannelConfigMapper channelConfigMapper;

    @Override
    public IPage<ChannelConfig> getPage(Integer page, Integer pageSize) {
        Page<ChannelConfig> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<ChannelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ChannelConfig::getCreateTime);
        return page(pageParam, wrapper);
    }

    @Override
    public void toggleStatus(Long id) {
        ChannelConfig config = getById(id);
        if (config != null) {
            config.setStatus(config.getStatus() == 1 ? 0 : 1);
            updateById(config);
        }
    }

    @Override
    public List<ChannelConfig> getActiveChannels() {
        LambdaQueryWrapper<ChannelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChannelConfig::getStatus, 1);
        return list(wrapper);
    }

    @Override
    public ChannelConfig getByType(String channelType) {
        LambdaQueryWrapper<ChannelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChannelConfig::getChannelType, channelType).eq(ChannelConfig::getStatus, 1);
        return getOne(wrapper);
    }
}