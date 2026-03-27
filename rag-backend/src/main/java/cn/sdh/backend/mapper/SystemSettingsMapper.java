package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.SystemSettings;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SystemSettingsMapper extends BaseMapper<SystemSettings> {

    @Select("SELECT * FROM system_settings WHERE setting_key = #{key}")
    SystemSettings selectByKey(@Param("key") String key);

    @Select("SELECT * FROM system_settings")
    List<SystemSettings> selectAll();
}