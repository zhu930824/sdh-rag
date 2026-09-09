package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.PlatformJob;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PlatformJobMapper extends BaseMapper<PlatformJob> {

    @Select("SELECT id FROM platform_job WHERE attempts < max_attempts AND " +
            "((status IN ('PENDING','RETRY_WAIT') AND available_at <= #{now}) " +
            "OR (status = 'RUNNING' AND lease_until < #{now})) " +
            "ORDER BY priority DESC, available_at ASC, id ASC LIMIT #{limit}")
    List<Long> selectClaimCandidates(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("UPDATE platform_job SET status='RUNNING', lease_owner=#{worker}, " +
            "lease_until=#{leaseUntil}, heartbeat_at=#{now}, attempts=attempts+1, " +
            "started_at=COALESCE(started_at, #{now}), error_message=NULL, update_time=#{now} " +
            "WHERE id=#{id} AND attempts < max_attempts AND " +
            "((status IN ('PENDING','RETRY_WAIT') AND available_at <= #{now}) " +
            "OR (status='RUNNING' AND lease_until < #{now}))")
    int claim(@Param("id") Long id, @Param("worker") String worker,
              @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("UPDATE platform_job SET lease_until=#{leaseUntil}, heartbeat_at=#{now}, update_time=#{now} " +
            "WHERE id=#{id} AND status='RUNNING' AND lease_owner=#{worker}")
    int heartbeat(@Param("id") Long id, @Param("worker") String worker,
                  @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("UPDATE platform_job SET status='SUCCEEDED', result_summary=#{summary}, error_message=NULL, " +
            "lease_owner=NULL, lease_until=NULL, finished_at=#{now}, update_time=#{now} " +
            "WHERE id=#{id} AND status IN ('RUNNING','CANCEL_REQUESTED') AND lease_owner=#{worker}")
    int complete(@Param("id") Long id, @Param("worker") String worker,
                 @Param("summary") String summary, @Param("now") LocalDateTime now);

    @Update("UPDATE platform_job SET status=#{status}, error_message=#{error}, available_at=#{availableAt}, " +
            "lease_owner=NULL, lease_until=NULL, finished_at=#{finishedAt}, update_time=#{now} " +
            "WHERE id=#{id} AND status='RUNNING' AND lease_owner=#{worker}")
    int fail(@Param("id") Long id, @Param("worker") String worker, @Param("status") String status,
             @Param("error") String error, @Param("availableAt") LocalDateTime availableAt,
             @Param("finishedAt") LocalDateTime finishedAt, @Param("now") LocalDateTime now);

    @Update("UPDATE platform_job SET status='CANCELLED', finished_at=#{now}, update_time=#{now} " +
            "WHERE id=#{id} AND status IN ('PENDING','RETRY_WAIT','FAILED','DEAD_LETTER')")
    int cancelWaiting(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE platform_job SET status='CANCEL_REQUESTED', update_time=#{now} " +
            "WHERE id=#{id} AND status='RUNNING'")
    int requestRunningCancellation(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE platform_job SET status='CANCELLED', lease_owner=NULL, lease_until=NULL, " +
            "finished_at=#{now}, update_time=#{now} WHERE id=#{id} " +
            "AND status='CANCEL_REQUESTED' AND lease_owner=#{worker}")
    int acknowledgeCancellation(@Param("id") Long id, @Param("worker") String worker,
                                @Param("now") LocalDateTime now);

    @Update("UPDATE platform_job SET status='PENDING', attempts=0, available_at=#{now}, error_message=NULL, " +
            "result_summary=NULL, lease_owner=NULL, lease_until=NULL, heartbeat_at=NULL, " +
            "started_at=NULL, finished_at=NULL, update_time=#{now} " +
            "WHERE id=#{id} AND status IN ('FAILED','DEAD_LETTER','CANCELLED')")
    int replay(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE platform_job SET status='DEAD_LETTER', error_message=COALESCE(error_message, '任务租约过期且已耗尽重试次数'), " +
            "lease_owner=NULL, lease_until=NULL, finished_at=#{now}, update_time=#{now} " +
            "WHERE status='RUNNING' AND lease_until < #{now} AND attempts >= max_attempts")
    int deadLetterExpired(@Param("now") LocalDateTime now);

    @Update("UPDATE platform_job SET status='CANCELLED', lease_owner=NULL, lease_until=NULL, " +
            "finished_at=#{now}, update_time=#{now} WHERE status='CANCEL_REQUESTED' AND lease_until < #{now}")
    int cancelExpiredRequested(@Param("now") LocalDateTime now);
}
