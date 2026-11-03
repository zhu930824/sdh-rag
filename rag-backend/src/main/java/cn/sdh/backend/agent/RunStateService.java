package cn.sdh.backend.agent;

import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.AgentStep;
import cn.sdh.backend.mapper.AgentRunMapper;
import cn.sdh.backend.mapper.AgentStepMapper;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.entity.ChatHistory;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Agent 运行状态落库:run/step 档案、状态 CAS、消息快照(供审批后 resume 重建上下文)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunStateService {

    /** 消息快照总字符上限,超出丢弃最旧轮次(由调用方截断单轮,此处兜底) */
    private static final int SNAPSHOT_CAP = 500_000;

    private final AgentRunMapper agentRunMapper;
    private final AgentStepMapper agentStepMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final RunExecutionLeaseService leases;

    public <T> T guarded(RunExecutionLeaseService.Fence fence, java.util.function.Supplier<T> write) {
        return leases.guarded(fence, write);
    }

    public boolean publish(RunExecutionLeaseService.Fence fence, AgentRun run, String expectedStatus,
                           String answer, String sources, String routerInfo, String trace) {
        if (!java.util.Objects.equals(run.getId(), fence.runId()) || !run.getRunKey().equals(fence.runKey())) {
            throw new BusinessException(409, "运行租约不匹配");
        }
        return guarded(fence, () -> {
            if (agentRunMapper.releaseLease(fence.runId(), fence.owner(), fence.epoch()) != 1) {
                throw new BusinessException(409, "运行执行权已失效");
            }
            if (!publish(run, expectedStatus, answer, sources, routerInfo, trace)) {
                throw new BusinessException(409, "运行状态已变化");
            }
            return true;
        });
    }

    /** Publish the visible reply and state atomically, so background consumers cannot see a half-written boundary. */
    @Transactional(rollbackFor = Exception.class)
    public boolean publish(AgentRun run, String expectedStatus, String answer,
                           String sources, String routerInfo, String trace) {
        if (expectedStatus != null && agentRunMapper.transition(run.getRunKey(), expectedStatus, run.getStatus()) != 1) {
            return false;
        }
        run.setUpdatedAt(LocalDateTime.now());
        if (agentRunMapper.updateById(run) != 1) throw new IllegalStateException("运行状态写入失败");
        if (run.getHistoryId() != null) {
            ChatHistory history = new ChatHistory(); history.setId(run.getHistoryId());
            history.setAnswer(answer); history.setSources(sources); history.setRouterInfo(routerInfo);
            history.setKnowledgeId(run.getKnowledgeId()); history.setModelId(run.getModelId());
            history.setAgentRunKey(run.getRunKey()); history.setAgentTrace(trace);
            history.setUpdateTime(LocalDateTime.now());
            if (chatHistoryMapper.updateById(history) != 1) throw new IllegalStateException("运行回答写入失败");
        }
        return true;
    }

    public AgentRun create(Long userId, String sessionId, String question,
                           Long knowledgeId, Long modelId, int maxSteps) {
        AgentRun run = new AgentRun();
        run.setRunKey(UUID.randomUUID().toString().replace("-", ""));
        run.setUserId(userId);
        run.setSessionId(sessionId);
        run.setQuestion(question);
        run.setKnowledgeId(knowledgeId);
        run.setModelId(modelId);
        run.setStatus(AgentRun.STATUS_RUNNING);
        run.setMaxSteps(maxSteps);
        run.setStepCount(0);
        run.setPromptTokens(0);
        run.setCompletionTokens(0);
        run.setTotalTokens(0);
        LocalDateTime now = LocalDateTime.now();
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        agentRunMapper.insert(run);
        return run;
    }

    /** 全量保存(调用方负责字段语义;统一刷新 updated_at) */
    public void save(AgentRun run) {
        run.setUpdatedAt(LocalDateTime.now());
        agentRunMapper.updateById(run);
    }

    /** 关联 chat_history 行 */
    public void bindHistoryId(Long runId, Long historyId) {
        AgentRun run = new AgentRun();
        run.setId(runId);
        run.setHistoryId(historyId);
        save(run);
    }

    /** 状态 CAS:仅 expected 时可迁到 next;返回是否成功(0=竞态/状态已变) */
    public boolean transition(String runKey, String expected, String next) {
        return agentRunMapper.transition(runKey, expected, next) == 1;
    }

    public AgentRun getByRunKey(String runKey) {
        return agentRunMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getRunKey, runKey));
    }

    public AgentRun getById(Long id) {
        return id == null ? null : agentRunMapper.selectById(id);
    }

    public void updatePendingAction(Long runId, Long actionId) { agentRunMapper.updatePendingAction(runId, actionId); }

    /** 追加步骤轨迹 */
    public AgentStep appendStep(RunExecutionLeaseService.Fence fence, Long runId, String stepType, String toolCode, Long actionId,
                                String inputJson, String outputJson, String status, Integer latencyMs, Integer tokens) {
        if (!java.util.Objects.equals(runId, fence.runId())) throw new BusinessException(409, "步骤与执行租约不匹配");
        return guarded(fence, () -> appendStep(runId, stepType, toolCode, actionId, inputJson, outputJson, status, latencyMs, tokens));
    }

    public AgentStep appendStep(Long runId, String stepType, String toolCode, Long actionId,
                                String inputJson, String outputJson, String status,
                                Integer latencyMs, Integer tokens) {
        AgentStep step = new AgentStep();
        step.setRunId(runId);
        step.setSeq(nextSeq(runId));
        step.setStepType(stepType);
        step.setToolCode(toolCode);
        step.setActionId(actionId);
        step.setInputJson(cap(inputJson, 256 * 1024));
        step.setOutputJson(cap(outputJson, 256 * 1024));
        step.setStatus(status);
        step.setLatencyMs(latencyMs);
        step.setTokens(tokens);
        step.setCreatedAt(LocalDateTime.now());
        agentStepMapper.insert(step);
        return step;
    }

    /** 步骤状态更新(updateById 默认忽略 null 字段,仅覆盖非空) */
    public void updateStep(RunExecutionLeaseService.Fence fence, AgentStep step) {
        if (!java.util.Objects.equals(step.getRunId(), fence.runId())) throw new BusinessException(409, "步骤与执行租约不匹配");
        guarded(fence, () -> { updateStep(step); return true; });
    }

    public void updateStep(AgentStep step) {
        if (step == null || step.getId() == null) return;
        agentStepMapper.updateById(step);
    }

    public List<AgentStep> stepsOf(Long runId) {
        return agentStepMapper.selectList(new LambdaQueryWrapper<AgentStep>()
                .eq(AgentStep::getRunId, runId)
                .orderByAsc(AgentStep::getSeq));
    }

    /** 消息快照:存 LLM 历史轮文本(数组 JSON),续跑时重建 assistant 消息 */
    public void persistRounds(Long runId, List<String> assistantTexts) {
        if (assistantTexts == null || assistantTexts.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessException(409, "运行消息快照必须是完整的文本数组");
        }
        int start = 0;
        String json = JSON.toJSONString(assistantTexts);
        while (json.length() > SNAPSHOT_CAP && start + 1 < assistantTexts.size()) {
            json = JSON.toJSONString(assistantTexts.subList(++start, assistantTexts.size()));
        }
        if (json.length() > SNAPSHOT_CAP) {
            throw new BusinessException(409, "单条运行消息超过快照上限，无法安全保存");
        }
        AgentRun run = new AgentRun();
        run.setId(runId);
        run.setMessagesJson(json);
        save(run);
    }

    /** 从快照恢复历史轮文本(与 persistRounds 对称) */
    public List<String> loadRounds(String messagesJson) {
        if (messagesJson == null || messagesJson.isBlank()) {
            return List.of();
        }
        try {
            Object decoded = JSON.parse(messagesJson);
            if (!(decoded instanceof List<?> messages)
                    || messages.stream().anyMatch(value -> !(value instanceof String))) {
                throw new IllegalArgumentException("Invalid conversation snapshot shape");
            }
            return messages.stream().map(String.class::cast).toList();
        } catch (Exception e) {
            // Do not log the parser message: it can contain private conversation content.
            log.warn("Agent 消息快照损坏，已阻止使用空上下文续跑");
            throw new BusinessException(409, "运行消息快照损坏，无法安全续跑", e);
        }
    }

    private int nextSeq(Long runId) {
        Long count = agentStepMapper.selectCount(new LambdaQueryWrapper<AgentStep>()
                .eq(AgentStep::getRunId, runId));
        return (count == null ? 0 : count.intValue()) + 1;
    }

    private String cap(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
