package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.WebhookConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface WebhookConfigMapper extends BaseMapper<WebhookConfig> {

    @Select("SELECT * FROM webhook_config WHERE status = 1")
    List<WebhookConfig> selectActiveWebhooks();

    @Select("SELECT * FROM webhook_config WHERE status = 1 AND events LIKE CONCAT('%', #{event}, '%')")
    List<WebhookConfig> selectByEvent(@Param("event") String event);

    @Update("UPDATE webhook_config SET last_trigger_time = NOW() WHERE id = #{id}")
    void updateTriggerTime(@Param("id") Long id);

    @Update("UPDATE webhook_config SET fail_count = fail_count + 1 WHERE id = #{id}")
    void incrementFailCount(@Param("id") Long id);

    @Update("UPDATE webhook_config SET fail_count = 0 WHERE id = #{id}")
    void resetFailCount(@Param("id") Long id);
}
