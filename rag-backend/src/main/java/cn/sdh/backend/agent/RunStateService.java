package cn.sdh.backend.agent;

import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.entity.AgentStep;
import cn.sdh.backend.mapper.AgentRunMapper;
import cn.sdh.backend.mapper.AgentStepMapper;
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

    /** 追加步骤轨迹 */
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
        AgentRun run = new AgentRun();
        run.setId(runId);
        String json = JSON.toJSONString(assistantTexts);
        run.setMessagesJson(json.length() > SNAPSHOT_CAP ? json.substring(json.length() - SNAPSHOT_CAP) : json);
        save(run);
    }

    /** 从快照恢复历史轮文本(与 persistRounds 对称) */
    public List<String> loadRounds(String messagesJson) {
        if (messagesJson == null || messagesJson.isBlank()) {
            return List.of();
        }
        try {
            return JSON.parseArray(messagesJson, String.class);
        } catch (Exception e) {
            log.warn("Agent 消息快照解析失败: {}", e.getMessage());
            return List.of();
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
