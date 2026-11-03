package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.AgentAction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentActionMapper extends BaseMapper<AgentAction> {
    /** Current read after a duplicate-key race; a repeatable-read snapshot may predate the winner. */
    @Select("SELECT * FROM agent_action WHERE tool_id=#{toolId} AND requested_by=#{actor} AND idempotency_key=#{key} FOR SHARE")
    @org.apache.ibatis.annotations.Options(useCache = false, flushCache = org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    AgentAction findIdempotentCurrent(@Param("toolId") Long toolId, @Param("actor") Long actor, @Param("key") String key);
    @Update("UPDATE agent_action SET status=#{a.status}, result_json=#{a.resultJson}, response_code=#{a.responseCode}, " +
            "error_message=#{a.errorMessage}, executed_at=#{a.executedAt}, update_time=NOW() " +
            "WHERE id=#{a.id} AND status='EXECUTING' AND execution_version=#{a.executionVersion}")
    int finishExecuting(@Param("a") AgentAction action);

    @Update("UPDATE agent_action SET status='UNKNOWN', error_message='执行进程中断或超时，请核对下游结果', update_time=NOW() " +
            "WHERE status='EXECUTING' AND update_time < #{cutoff}")
    int recoverInterrupted(@Param("cutoff") java.time.LocalDateTime cutoff);
    @Update("UPDATE agent_action SET status=#{outcome}, result_json=#{receipt}, " +
            "error_message=CONCAT('人工核对 actor=', #{actor}, ': ', #{receipt}), update_time=NOW() " +
            "WHERE id=#{id} AND status='UNKNOWN'")
    int reconcile(@Param("id") Long id, @Param("outcome") String outcome,
                  @Param("receipt") String receipt, @Param("actor") Long actor);
    @Update("UPDATE agent_action SET status = #{next}, update_time = NOW() " +
            "WHERE id = #{id} AND status = #{expected}")
    int transition(@Param("id") Long id, @Param("expected") String expected, @Param("next") String next);

    @Update("UPDATE agent_action SET status='EXECUTING', started_at=NOW(), update_time=NOW() " +
            "WHERE id=#{id} AND status='READY' AND execution_version=#{version}")
    int claimReady(@Param("id") Long id, @Param("version") int version);

    @Update("UPDATE agent_action SET execution_job_id=#{jobId}, update_time=NOW() " +
            "WHERE id=#{id} AND status='READY' AND execution_version=#{version}")
    int bindExecutionJob(@Param("id") Long id, @Param("version") int version, @Param("jobId") Long jobId);

    @Update("UPDATE agent_action SET status='READY', execution_version=execution_version+1, execution_job_id=NULL, " +
            "error_message=NULL, result_json=NULL, response_code=NULL, executed_at=NULL, started_at=NULL, update_time=NOW() " +
            "WHERE id=#{id} AND status='FAILED'")
    int prepareRetry(@Param("id") Long id);

    @Update("UPDATE agent_action SET status='FAILED', error_message=#{reason}, update_time=NOW() " +
            "WHERE id=#{id} AND status='READY' AND execution_version=#{version}")
    int failBeforeExecution(@Param("id") Long id, @Param("version") int version, @Param("reason") String reason);

    @Select("SELECT a.* FROM agent_action a LEFT JOIN platform_job j ON j.id=a.execution_job_id " +
            "WHERE a.status='READY' AND (j.id IS NULL OR j.status IN ('DEAD_LETTER','CANCELLED','FAILED','SUCCEEDED')) " +
            "ORDER BY a.update_time, a.id LIMIT 100")
    java.util.List<AgentAction> selectRecoveryCandidates();

    @Update("UPDATE agent_action SET status = 'READY', decided_at = NOW(), update_time = NOW() " +
            "WHERE id = #{id} AND approver_id = #{actor} AND status = 'PENDING_APPROVAL'")
    int approve(@Param("id") Long id, @Param("actor") Long actor);

    @Update("UPDATE agent_action SET status = 'REJECTED', error_message = #{reason}, " +
            "decided_at = NOW(), update_time = NOW() " +
            "WHERE id = #{id} AND approver_id = #{actor} AND status = 'PENDING_APPROVAL'")
    int reject(@Param("id") Long id, @Param("actor") Long actor, @Param("reason") String reason);
}
