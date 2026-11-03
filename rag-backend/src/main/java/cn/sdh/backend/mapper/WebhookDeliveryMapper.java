package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.WebhookDelivery;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface WebhookDeliveryMapper extends BaseMapper<WebhookDelivery> {
    @Update("""
            UPDATE webhook_delivery SET status = 'RUNNING', attempts = #{attempts},
                error_message = NULL WHERE id = #{id} AND status NOT IN ('SUCCEEDED', 'SKIPPED')
            """)
    int markRunning(@Param("id") Long id, @Param("attempts") int attempts);

    @Update("""
            UPDATE webhook_delivery SET status = 'SUCCEEDED', delivered_at = #{now},
                error_message = NULL WHERE id = #{id}
            """)
    int markSucceeded(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE webhook_delivery SET status = #{status}, error_message = #{error} WHERE id = #{id}")
    int markFailed(@Param("id") Long id, @Param("status") String status, @Param("error") String error);

    @Update("UPDATE webhook_delivery SET status = 'SKIPPED', error_message = #{reason} WHERE id = #{id}")
    int markSkipped(@Param("id") Long id, @Param("reason") String reason);
}
