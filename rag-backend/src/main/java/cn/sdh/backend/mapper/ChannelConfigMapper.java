package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.ChannelConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChannelConfigMapper extends BaseMapper<ChannelConfig> {

    @Select("SELECT * FROM channel_config WHERE status = 1")
    List<ChannelConfig> selectActive();

    @Select("SELECT * FROM channel_config WHERE channel_type = #{channelType} AND status = 1")
    List<ChannelConfig> selectByType(@Param("channelType") String channelType);
}