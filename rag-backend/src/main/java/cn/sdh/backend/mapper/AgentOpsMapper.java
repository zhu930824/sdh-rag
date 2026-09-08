package cn.sdh.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AgentOpsMapper {
    @Select("SELECT COUNT(*) totalRuns, " +
            "COALESCE(SUM(status='COMPLETED'),0) completedRuns, COALESCE(SUM(status='FAILED'),0) failedRuns, " +
            "COALESCE(SUM(status='WAITING_APPROVAL'),0) waitingRuns, COALESCE(SUM(status='REJECTED'),0) rejectedRuns, " +
            "COALESCE(ROUND(AVG(step_count),1),0) avgSteps, COALESCE(SUM(total_tokens),0) totalTokens " +
            "FROM agent_run WHERE user_id=#{actor} AND created_at>=#{since}")
    Map<String, Object> runOverview(@Param("actor") Long actor, @Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) totalActions, COALESCE(SUM(status='SUCCEEDED'),0) succeededActions, " +
            "COALESCE(SUM(status='FAILED'),0) failedActions, COALESCE(SUM(status='REJECTED'),0) rejectedActions, " +
            "COALESCE(SUM(status='PENDING_APPROVAL'),0) pendingActions " +
            "FROM agent_action WHERE requested_by=#{actor} AND requested_at>=#{since}")
    Map<String, Object> actionOverview(@Param("actor") Long actor, @Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) feedbackCount, COALESCE(SUM(f.reusable_knowledge=1),0) reusableCount, " +
            "COALESCE(SUM(f.outcome='FAILED'),0) businessFailedCount " +
            "FROM agent_action_feedback f JOIN agent_action a ON a.id=f.action_id " +
            "WHERE a.requested_by=#{actor} AND f.create_time>=#{since}")
    Map<String, Object> feedbackOverview(@Param("actor") Long actor, @Param("since") LocalDateTime since);

    @Select("SELECT a.tool_id toolId, MAX(a.tool_name) toolName, COUNT(*) calls, " +
            "COALESCE(SUM(a.status='SUCCEEDED'),0) succeeded, COALESCE(SUM(a.status='FAILED'),0) failed, " +
            "COALESCE(SUM(a.status='REJECTED'),0) rejected " +
            "FROM agent_action a WHERE a.requested_by=#{actor} AND a.requested_at>=#{since} " +
            "GROUP BY a.tool_id ORDER BY calls DESC LIMIT 10")
    List<Map<String, Object>> toolMetrics(@Param("actor") Long actor, @Param("since") LocalDateTime since);

    @Select("SELECT DATE(created_at) day, COUNT(*) runs, COALESCE(SUM(status='COMPLETED'),0) completed, " +
            "COALESCE(SUM(status='FAILED'),0) failed FROM agent_run " +
            "WHERE user_id=#{actor} AND created_at>=#{since} GROUP BY DATE(created_at) ORDER BY day")
    List<Map<String, Object>> dailyTrend(@Param("actor") Long actor, @Param("since") LocalDateTime since);
}
