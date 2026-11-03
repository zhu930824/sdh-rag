package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.TransactionalOutbox;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TransactionalOutboxMapper extends BaseMapper<TransactionalOutbox> {
    @Select("""
            SELECT id FROM transactional_outbox
            WHERE ((status IN ('NEW', 'RETRY_WAIT') AND available_at <= #{now})
                OR (status = 'RELAYING' AND lease_until < #{now}))
              AND relay_attempts < max_attempts
            ORDER BY create_time ASC
            LIMIT #{limit}
            """)
    List<Long> selectRelayCandidates(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE transactional_outbox
            SET status = 'RELAYING', relay_attempts = relay_attempts + 1,
                lease_owner = #{workerId}, lease_until = #{leaseUntil}, error_message = NULL
            WHERE id = #{id}
              AND ((status IN ('NEW', 'RETRY_WAIT') AND available_at <= #{now})
                OR (status = 'RELAYING' AND lease_until < #{now}))
              AND relay_attempts < max_attempts
            """)
    int claim(@Param("id") Long id, @Param("workerId") String workerId,
              @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("""
            UPDATE transactional_outbox
            SET status = 'PUBLISHED', published_at = #{now}, lease_owner = NULL,
                lease_until = NULL, error_message = NULL
            WHERE id = #{id} AND status = 'RELAYING' AND lease_owner = #{workerId}
            """)
    int markPublished(@Param("id") Long id, @Param("workerId") String workerId,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE transactional_outbox
            SET status = #{status}, available_at = #{availableAt}, error_message = #{error},
                lease_owner = NULL, lease_until = NULL
            WHERE id = #{id} AND status = 'RELAYING' AND lease_owner = #{workerId}
            """)
    int markFailed(@Param("id") Long id, @Param("workerId") String workerId,
                   @Param("status") String status, @Param("availableAt") LocalDateTime availableAt,
                   @Param("error") String error);

    @Update("""
            UPDATE transactional_outbox
            SET status = 'DEAD_LETTER', lease_owner = NULL, lease_until = NULL,
                error_message = COALESCE(error_message, 'Outbox租约过期且已耗尽重试次数')
            WHERE status = 'RELAYING' AND lease_until < #{now} AND relay_attempts >= max_attempts
            """)
    int deadLetterExpired(@Param("now") LocalDateTime now);
}
