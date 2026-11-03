package cn.sdh.backend.agent;

import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.mapper.AgentRunMapper;
import cn.sdh.backend.mapper.AgentActionMapper;
import cn.sdh.backend.service.AgentActionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

/** Rebuilds missing resume deliveries from durable approval facts after a restart. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentResumeRecoveryScheduler {
    private final AgentRunMapper runs;
    private final AgentActionService actions;
    private final AgentApprovalRouter router;
    private final AgentActionMapper actionMapper;
    private final RunExecutionLeaseService leases;
    @Scheduled(fixedDelayString = "${xushu.agent.recovery-delay-ms:15000}")
    public void recover() {
        leases.interruptExpired();
        // HTTP tools have a 120-second maximum timeout. Never replay stale writes automatically.
        actionMapper.recoverInterrupted(LocalDateTime.now().minusMinutes(5));
        for (AgentRun run : runs.selectList(new LambdaQueryWrapper<AgentRun>()
                .in(AgentRun::getStatus, AgentRun.STATUS_WAITING_APPROVAL, AgentRun.STATUS_WAITING_ACTION)
                .lt(AgentRun::getUpdatedAt, LocalDateTime.now().minusSeconds(30))
                .orderByAsc(AgentRun::getUpdatedAt).last("LIMIT 100"))) {
            try {
                List<AgentAction> related = actions.listByRun(run.getId());
                if (!related.isEmpty() && related.stream().noneMatch(action ->
                        AgentActionState.blocksContinuation(action.getStatus()))) {
                    router.onActionDecided(related.getLast(), false);
                }
            } catch (Exception failure) {
                log.warn("Agent 续跑恢复投递失败 runId={}", run.getId());
            }
        }
    }
}
