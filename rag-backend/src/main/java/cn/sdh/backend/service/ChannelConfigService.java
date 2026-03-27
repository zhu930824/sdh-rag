package cn.sdh.backend.service;

import cn.sdh.backend.entity.ChannelConfig;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ChannelConfigService extends IService<ChannelConfig> {

    IPage<ChannelConfig> getPage(Integer page, Integer pageSize);

    void toggleStatus(Long id);

    List<ChannelConfig> getActiveChannels();

    ChannelConfig getByType(String channelType);
}