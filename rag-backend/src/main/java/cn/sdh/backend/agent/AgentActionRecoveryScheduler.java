package cn.sdh.backend.agent;

import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.mapper.AgentActionMapper;
import cn.sdh.backend.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentActionRecoveryScheduler {
    private final AgentActionMapper actions;
    private final PlatformJobService jobs;
    private final AgentActionJobs actionJobs;
    private final AgentActionCompletionService completion;
    @Scheduled(fixedDelayString = "${xushu.agent.recovery-delay-ms:15000}")
    public void recover() {
        for (AgentAction action : actions.selectRecoveryCandidates()) {
            try {
                PlatformJob job = action.getExecutionJobId() == null ? null : jobs.get(action.getExecutionJobId());
                if (job == null) actionJobs.enqueueExecution(action);
                else if (List.of(PlatformJob.DEAD_LETTER, PlatformJob.CANCELLED, PlatformJob.FAILED, PlatformJob.SUCCEEDED)
                        .contains(job.getStatus())) {
                    completion.failBeforeExecution(action, "执行任务已结束但动作尚未开始，请检查任务后显式重试");
                }
            } catch (Exception failure) {
                log.warn("动作任务恢复失败 actionId={}", action.getId());
            }
        }
    }
}
