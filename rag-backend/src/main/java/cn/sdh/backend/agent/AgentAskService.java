package cn.sdh.backend.agent;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.config.IntentRouterConfig;
import cn.sdh.backend.dto.AgentAskConfig;
import cn.sdh.backend.dto.IntentRouterResponse;
import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.entity.AgentStep;
import cn.sdh.backend.entity.AgentTool;
import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.service.AgentActionService;
import cn.sdh.backend.service.AgentToolService;
import cn.sdh.backend.service.IntentRouterService;
import cn.sdh.backend.service.ModelConfigService;
import cn.sdh.backend.service.factory.ModelProvider;
import cn.sdh.backend.service.impl.ChatServiceImpl;
import cn.sdh.backend.agent.runtime.AgentRuntimePort;
import cn.sdh.backend.agent.runtime.AgentScopeRuntimeAdapter;
import cn.sdh.backend.service.PlatformJobService;
import org.springframework.beans.factory.annotation.Value;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 应用协调入口：准备上下文、领取执行权、选择引擎并发送兼容 SSE 事件。
 * 公共模型/系统上下文由 AgentExecutionPreparation 准备；
 * 进度和结果由 AgentRunLifecycle 经原有持久化事务发布。
 * 业务工具统一经过动作账本、审批与可靠任务，SDK 不直接执行业务 HTTP。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAskService {

    private static final int DEFAULT_MAX_STEPS = AgentRunBudget.DEFAULT_MAX_STEPS;
    private static final int MAX_STEPS_LIMIT = 20;
    private static final int EVIDENCE_CAP = 12 * 1024;
    private static final int ROUND_TEXT_CAP = 20 * 1024;
    private static final int RESULT_TO_LLM_CAP = 6 * 1024;
    private static final int ARGS_PREVIEW_CAP = 2 * 1024;
    private static final int TOKEN_HARD_CAP = AgentRunBudget.TOKEN_LIMIT;

    private final ChatServiceImpl chatService;
    private final AgentActionService agentActionService;
    private final AgentToolService agentToolService;
    private final RunStateService runStateService;
    private final ModelConfigService modelConfigService;
    private final IntentRouterService intentRouterService;
    private final IntentRouterConfig intentRouterConfig;
    private final AgentScopeRuntimeAdapter agentScopeRuntime;
    private final PlatformJobService jobs;
    private final RunExecutionLeaseService leases;
    private final AgentExecutionPreparation preparation;
    private final AgentRunLifecycle lifecycle;

    @Value("${xushu.agent.runtime:legacy}")
    private String runtimeEngine = "legacy";

    // ===================== 对外入口 =====================

    /**
     * Agent 模式问答(SSE 事件流)。预备工作在请求线程完成(保证 UserContext 语义),
     * LLM 决策循环在 boundedElastic 上执行。
     */
    public Flux<String> ask(String question, String sessionId, Long userId, Long knowledgeId, Long modelId,
                            boolean memoryEnabled, Map<String, Object> metadataFilters, AgentAskConfig agentCfg) {
        int maxSteps = resolveMaxSteps(agentCfg);
        Prepared prepared;
        try {
            prepared = prepare(question, sessionId, userId, knowledgeId, modelId,
                    memoryEnabled, metadataFilters, maxSteps);
        } catch (BusinessException e) {
            return Flux.just(errorEvent(e.getMessage()));
        }
        return execute(prepared);
    }

    /** 可复用的"继续一轮决策"入口:resume 时以 run 快照重建 Prepared 后调用(见 resume 场景) */
    public Flux<String> execute(Prepared prepared) {
        return execute(prepared, () -> {});
    }

    public Flux<String> execute(Prepared prepared, Runnable ownerCheckpoint) {
        return Flux.<String>create(sink -> {
            try (var lease = leases.claim(prepared.runKey(), prepared.userId(), ownerCheckpoint)) {
                runLoop(prepared.withLease(lease), sink);
            } catch (Exception failure) {
                // A losing subscriber must never fail or overwrite the winning execution.
                emit(sink, errorEvent(failure.getMessage()));
                sink.complete();
            }
        }, FluxSink.OverflowStrategy.ERROR)
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ===================== 预备 =====================

    private Prepared prepare(String question, String sessionId, Long userId, Long knowledgeId, Long modelId,
                             boolean memoryEnabled, Map<String, Object> metadataFilters, int maxSteps) {
        if (!List.of("legacy", "agentscope").contains(runtimeEngine)) {
            throw new BusinessException(500, "Agent runtime 配置必须为 legacy 或 agentscope");
        }
        String currentSessionId = (sessionId != null && !sessionId.isEmpty())
                ? sessionId : UUID.randomUUID().toString();

        var binding = preparation.model(modelId, AgentExecutionPreparation.Purpose.INITIAL);
        ModelConfig modelConfig = binding.config();
        ModelProvider provider = binding.provider();
        ChatModel model = binding.model();

        // 智能路由(与普通问答一致:显式指定知识库则跳过)
        Long effectiveKb = knowledgeId;
        String routerPayloadJson = null;
        String routerEventJson = null;
        if (effectiveKb == null && intentRouterConfig.isEnabled()) {
            try {
                IntentRouterResponse route = intentRouterService.route(question, userId);
                if (route.isNeedRetrieval() && route.getTopKnowledgeBaseId() != null) {
                    effectiveKb = route.getTopKnowledgeBaseId();
                }
                Map<String, Object> payload = routerPayload(route);
                routerPayloadJson = writeJson(payload);
                if (intentRouterConfig.isShowRouterInfo()) {
                    // 事件协议与普通问答一致:{type:"routerInfo", routerInfo:{...}}
                    routerEventJson = writeJson(Map.of("type", "routerInfo", "routerInfo", payload));
                }
            } catch (Exception e) {
                log.warn("Agent 意图路由失败,按无路由继续: {}", e.getMessage());
            }
        }

        // 检索(证据)与来源
        String evidence = "";
        String sourcesEvent = null;
        String sourcesPayloadJson = null;
        if (effectiveKb != null) {
            ChatServiceImpl.AgentRetrieval retrieval = chatService.retrieveForAgent(
                    question, effectiveKb, userId, currentSessionId, metadataFilters);
            if (retrieval.forbidden()) {
                throw new BusinessException(403, "无权访问该知识库");
            }
            if (retrieval.context() != null && !retrieval.context().isBlank()) {
                evidence = truncate(retrieval.context(), EVIDENCE_CAP);
            }
            sourcesEvent = retrieval.sourcesEvent();
            sourcesPayloadJson = retrieval.sourcesPayloadJson();
        }

        List<AgentTool> tools = agentToolService.listUsable(userId);

        Long historyId = chatService.saveQuestionRow(question, currentSessionId, userId,
                effectiveKb, modelConfig.getId());
        AgentRun run = runStateService.create(userId, currentSessionId, question,
                effectiveKb, modelConfig.getId(), maxSteps);
        run.setEngine(runtimeEngine);
        run.setEngineVersion("agentscope".equals(runtimeEngine) ? "2.0.3" : "1");
        run.setMemoryEnabled(memoryEnabled);
        runStateService.save(run);
        runStateService.bindHistoryId(run.getId(), historyId);

        String system = preparation.system(evidence, tools, userId, currentSessionId, question, memoryEnabled);

        AgentRun persisted = run;
        persisted.setEvidenceText(evidence == null || evidence.isBlank() ? null : truncate(evidence, EVIDENCE_CAP));
        runStateService.save(persisted);

        return new Prepared(run.getRunKey(), run.getId(), historyId, currentSessionId, question, userId,
                effectiveKb, modelConfig.getId(), model, provider, system, memoryEnabled,
                sourcesEvent, sourcesPayloadJson, routerEventJson, routerPayloadJson, evidence, tools, maxSteps,
                false, null, null);
    }

    // ===================== 审批后续跑(resume) =====================

    /**
     * 审批已决定后的续跑入口(状态 CAS 在调用线程完成,保证 409 语义)。
     * 允许触发者:run 发起人,或该 run 任一待审批动作的审批人。
     */
    public Flux<String> resumeFlux(String runKey, Long actor, Long decidedActionId) {
        AgentRun run = runStateService.getByRunKey(runKey);
        if (run == null) {
            throw new BusinessException(404, "Agent 运行不存在");
        }
        requireResumeActor(run, actor);
        if (decidedActionId == null) {
            decidedActionId = run.getPendingActionId();
        }
        AgentAction decided = null;
        for (AgentAction action : agentActionService.listByRun(run.getId())) {
            if (action.getId().equals(decidedActionId)) {
                decided = action;
                break;
            }
        }
        if (decided == null) {
            throw new BusinessException(404, "未找到本次审批对应的动作记录");
        }
        if (AgentActionState.blocksContinuation(decided.getStatus())) {
            throw new BusinessException(409, "动作尚未完成审批与执行，不能续跑");
        }
        boolean hasPending = agentActionService.listByRun(run.getId()).stream()
                .anyMatch(item -> AgentActionState.blocksContinuation(item.getStatus()));
        if (hasPending) {
            throw new BusinessException(409, "本次运行仍有未决定动作，全部处理后才能续跑");
        }
        if (!AgentRun.isWaiting(run.getStatus())) {
            throw new BusinessException(409, "运行状态不是等待审批(当前:" + run.getStatus()
                    + "),请刷新页面获取最新状态");
        }
        if ("REJECTED".equals(decided.getStatus())) {
            String answer = AgentRunLifecycle.rejectionAnswer(decided);
            finalizeRejected(decided);
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("type", "done");
            done.put("historyId", run.getHistoryId());
            done.put("runId", run.getRunKey());
            return Flux.just(writeJson(Map.of("type", "content", "content", answer)),
                    writeJson(Map.of("type", "agent_status", "status", AgentRun.STATUS_REJECTED)),
                    writeJson(done));
        }
        runStateService.loadRounds(run.getMessagesJson());
        Prepared prepared = preparedFromRun(run, decided);
        if (!runStateService.transition(runKey, run.getStatus(), AgentRun.STATUS_RUNNING)) {
            throw new BusinessException(409, "运行已在续跑或状态已变化,请刷新页面");
        }
        return execute(prepared);
    }

    /** 后台续跑(审批中心 approve 缺省路径):fire-and-forget,终态落 chat_history */
    public void resumeInBackground(String runKey, Long decidedActionId) {
        AgentRun run = runStateService.getByRunKey(runKey);
        if (run == null || !AgentRun.isWaiting(run.getStatus())) return;
        if (agentActionService.listByRun(run.getId()).stream().anyMatch(action ->
                AgentActionState.blocksContinuation(action.getStatus()))) {
            throw new BusinessException(409, "动作仍待审批、执行或核对，暂不能提交续跑任务");
        }
        Long actionId = decidedActionId == null ? run.getPendingActionId() : decidedActionId;
        if (actionId == null) throw new BusinessException(409, "运行没有可关联的续跑动作");
        AgentAction trigger = agentActionService.listByRun(run.getId()).stream()
                .filter(action -> action.getId().equals(actionId)).findFirst()
                .orElseThrow(() -> new BusinessException(409, "续跑动作不属于本次运行"));
        jobs.enqueue(new PlatformJobService.EnqueueCommand("agent-resume:" + runKey + ":" + actionId + ":"
                + cn.sdh.backend.service.AgentActionJobs.version(trigger),
                "AGENT_RESUME", "AGENT_RUN", run.getId(),
                writeJson(Map.of("runKey", runKey, "actionId", actionId)), 5, 1, run.getUserId()));
    }

    /** 拒绝路径(确定性收尾,不回调 LLM):写入最终文本与状态 */
    public void finalizeRejected(AgentAction action) {
        try {
            if (lifecycle.rejectWaiting(action)) log.info("Agent 运行被拒绝并收尾 runId={}", action.getRunId());
        } catch (Exception failure) { log.warn("Agent 拒绝收尾失败: {}", failure.getMessage()); }
    }

    private Prepared preparedFromRun(AgentRun run, AgentAction decided) {
        var binding = preparation.model(run.getModelId(), AgentExecutionPreparation.Purpose.RESUME);
        ModelConfig modelConfig = binding.config();
        ModelProvider provider = binding.provider();
        ChatModel model = binding.model();
        List<AgentTool> tools = agentToolService.listUsable(run.getUserId());
        boolean memoryEnabled = !Boolean.FALSE.equals(run.getMemoryEnabled());
        String system = preparation.system(run.getEvidenceText(), tools, run.getUserId(), run.getSessionId(), run.getQuestion(), memoryEnabled);

        String note = decided == null ? null : approvalNote(decided);
        // 续跑不重放 routerInfo/sources;suppressStart=true
        return new Prepared(run.getRunKey(), run.getId(), run.getHistoryId(), run.getSessionId(),
                run.getQuestion(), run.getUserId(), run.getKnowledgeId(), modelConfig.getId(),
                model, provider, system, memoryEnabled, null, null, null, null, run.getEvidenceText(),
                tools, run.getMaxSteps() == null ? DEFAULT_MAX_STEPS : run.getMaxSteps(),
                true, note, null);
    }

    /** Called only after the recovery job has atomically reset a run without registered actions. */
    public Flux<String> restartRecovered(String runKey, Long actor, Runnable checkpoint) {
        AgentRun run = runStateService.getByRunKey(runKey);
        if (run == null || !java.util.Objects.equals(run.getUserId(), actor)) throw new BusinessException(403, "运行归属不一致");
        if (!AgentRun.STATUS_RUNNING.equals(run.getStatus()) || run.getLeaseOwner() != null
                || !agentActionService.listByRun(run.getId()).isEmpty()) {
            throw new BusinessException(409, "运行不符合重新执行条件");
        }
        return execute(preparedFromRun(run, null), checkpoint);
    }

    /** 审批结果注记(以 UserMessage 追加在暂停说明之后,使模型在准确位置看到结果) */
    private String approvalNote(AgentAction action) {
        String summary;
        if ("SUCCEEDED".equals(action.getStatus())) {
            String result = action.getResultJson() == null || action.getResultJson().isBlank()
                    ? "无返回内容" : truncate(action.getResultJson(), 4 * 1024);
            summary = "执行成功,结果: " + result;
        } else if ("REJECTED".equals(action.getStatus())) {
            summary = "已被拒绝,原因: "
                    + (action.getErrorMessage() == null ? "审批人拒绝" : action.getErrorMessage());
        } else {
            summary = "执行失败,原因: "
                    + (action.getErrorMessage() == null ? "未知错误" : action.getErrorMessage());
        }
        return "【审批结果通知】工具「" + action.getToolName() + "」(id=" + action.getId() + ") " + summary
                + "。若任务已因此完成请直接给出结论;若还需其他信息,可继续调用其他工具完成剩余步骤。";
    }

    private void requireResumeActor(AgentRun run, Long actor) {
        if (actor == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (actor.equals(run.getUserId())) return;
        for (AgentAction action : agentActionService.listByRun(run.getId())) {
            if (actor.equals(action.getApproverId())) return;
        }
        throw new BusinessException(403, "仅运行发起人或动作审批人可继续该运行");
    }

    // ===================== 决策循环 =====================

    private void runLoop(Prepared p, FluxSink<String> sink) {
        LoopState st = new LoopState();
        try {
            if (p.routerEventJson() != null) {
                emit(sink, p.routerEventJson());
            }
            if (p.sourcesEvent() != null) {
                emit(sink, p.sourcesEvent());
            }
            if (!p.suppressStart()) {
                emitEvent(sink, Map.of("type", "agent_start", "runId", p.runKey(), "maxSteps", p.maxSteps()));
            }

            AgentRun run = runStateService.getByRunKey(p.runKey());
            if ("agentscope".equals(run.getEngine())) {
                runAgentScope(p, sink, st, run);
                return;
            }
            if (p.suppressStart() && run != null) {
                st.assistantTexts.addAll(runStateService.loadRounds(run.getMessagesJson()));
            }
            AgentRunBudget budget = AgentRunBudget.from(run);
            budget.requireAvailable();
            int completedSteps = budget.usedSteps();
            st.promptTokens = run.getPromptTokens() == null ? 0 : run.getPromptTokens();
            st.completionTokens = run.getCompletionTokens() == null ? 0 : run.getCompletionTokens();
            st.carriedTokens = Math.toIntExact(budget.usedTokens() - st.promptTokens - st.completionTokens);

            int turn = 0;
            for (turn = 1; turn <= budget.remainingSteps(); turn++) {
                p.lease().requireAlive();
                leases.guarded(p.lease().fence(), () -> true);
                List<ToolCallback> callbacks = p.tools().stream()
                        .map(tool -> new AgentToolCallback(tool, executorFor(p, st, sink)))
                        .map(t -> (ToolCallback) t)
                        .toList();
                ChatOptions options = runtimeOptions(p.provider(), callbacks);

                long llmStart = System.currentTimeMillis();
                ChatResponse response;
                try {
                    List<Message> messages = buildMessages(p.system(), p.question(),
                            st.assistantTexts, p.resumeNote());
                    response = options == null ? p.model().call(new Prompt(messages))
                            : p.model().call(new Prompt(messages, options));
                } catch (Exception e) {
                    log.error("Agent 决策轮调用失败 turn={}: {}", turn, e.getMessage(), e);
                    throw new BusinessException(500, "模型调用失败: " + e.getMessage(), e);
                }
                int latency = (int) (System.currentTimeMillis() - llmStart);
                p.lease().requireAlive();

                String text = response.getResult() == null ? null : response.getResult().getOutput().getText();
                int[] usage = readUsage(response);
                st.promptTokens = Math.addExact(st.promptTokens, usage[0]);
                st.completionTokens = Math.addExact(st.completionTokens, usage[1]);

                if (text != null && !text.isBlank()) {
                    st.assistantTexts.add(truncate(text, ROUND_TEXT_CAP));
                }
                st.stepCount = completedSteps + turn;
                runStateService.appendStep(p.lease().fence(), run.getId(), AgentStep.STEP_TYPE_LLM, null, null,
                        null, truncate(text, ROUND_TEXT_CAP), "SUCCEEDED", latency,
                        usage[0] + usage[1]);
                persistProgress(p, st);

                if (!st.pending.isEmpty()) {
                    // 审批暂停:整轮收尾,无 done
                    pauseForApproval(p, sink, st, text);
                    return;
                }
                if (text != null && !text.isBlank()) {
                    // 正常终态回答
                    completeRun(p, sink, st, text);
                    return;
                }
                // 模型无文本且无待审批(异常空轮),继续只会烧 token,直接失败
                throw new BusinessException(500, "模型未产生有效回答(第 " + turn + " 轮,无待审批动作)");
            }
            throw new BusinessException(500, "超出最大决策步数(" + p.maxSteps() + ")");
        } catch (Throwable e) {
            log.error("Agent 运行失败 runKey={}: {}", p.runKey(), e.getMessage(), e);
            failRun(p, sink, e);
        } finally {
            if (sink != null && !sink.isCancelled()) {
                sink.complete();
            }
        }
    }

    /** 审批暂停收尾:状态落库 + 事件推送(流正常结束,无 done 事件) */
    private void pauseForApproval(Prepared p, FluxSink<String> sink, LoopState st, String note) {
        p.lease().requireAlive();
        List<Long> pendingIds = st.pending.stream().map(pi -> pi.action().getId()).toList();

        AgentRun run = lifecycle.pause(publication(p), st.progress(), st.pending.stream().map(PendingInfo::action).toList());
        String waitingStatus = run.getStatus();
        if (note != null && !note.isBlank()) emitContent(sink, note);
        emitEvent(sink, Map.of("type", "agent_status", "status", waitingStatus,
                "pendingActionIds", pendingIds));
        for (PendingInfo info : st.pending) {
            AgentAction action = info.action();
            if ("UNKNOWN".equals(action.getStatus())) {
                emitContent(sink, "业务动作「" + info.toolName() + "」结果待核对，请到业务动作中心确认下游结果后继续。");
                continue;
            }
            if (List.of("READY", "EXECUTING").contains(action.getStatus())) {
                emitContent(sink, "业务动作「" + info.toolName() + "」已进入执行队列，完成后会自动继续。");
                continue;
            }
            Map<String, Object> evt = new LinkedHashMap<>();
            evt.put("type", "approval_required");
            evt.put("runId", p.runKey());
            evt.put("actionId", action.getId());
            evt.put("toolCode", info.toolCode());
            evt.put("toolName", info.toolName());
            evt.put("argsPreview", preview(action.getRequestJson(), ARGS_PREVIEW_CAP));
            if (action.getEvidenceJson() != null) {
                evt.put("evidencePreview", preview(action.getEvidenceJson(), ARGS_PREVIEW_CAP));
            }
            evt.put("canApprove", p.userId().equals(action.getApproverId()));
            emit(sink, writeJson(evt));
        }
        log.info("Agent 运行暂停等待审批 runKey={}, pending={}", p.runKey(), pendingIds.size());
    }

    /** 正常完成:原子发布 → 内容事件 → 尝试写记忆 → done */
    private void completeRun(Prepared p, FluxSink<String> sink, LoopState st, String answer) {
        completeRun(p, sink, st, answer, false);
    }

    private void completeRun(Prepared p, FluxSink<String> sink, LoopState st, String answer, boolean alreadyStreamed) {
        p.lease().requireAlive();
        AgentRun run = lifecycle.complete(publication(p), st.progress(), answer);
        if (!alreadyStreamed) emitContent(sink, answer);
        if (p.memoryEnabled()) {
            try { chatService.storeMemoryAfterTurn(p.userId(), p.sessionId(), p.question(), answer, true); }
            catch (Exception failure) { log.warn("Agent 回答已发布，记忆写入失败 runKey={}", p.runKey()); }
        }
        emit(sink, "{\"type\":\"done\",\"historyId\":" + p.historyId()
                + ",\"runId\":\"" + p.runKey() + "\"}");
        log.info("Agent 运行完成 runKey={}, steps={}, tokens={}",
                p.runKey(), st.stepCount, run.getTotalTokens());
    }

    /** 失败收尾:run=FAILED + error 事件 */
    private void failRun(Prepared p, FluxSink<String> sink, Throwable failure) {
        AgentRunTermination termination = AgentRunTermination.from(failure);
        String message = termination.message();
        boolean published = false;
        try {
            published = lifecycle.fail(publication(p), failure);
        } catch (Exception logError) {
            log.warn("Agent 失败状态落库异常: {}", logError.getMessage());
        }
        if (published) emitEvent(sink, Map.of("type", "agent_status", "status", termination.status()));
        emit(sink, errorEvent(message));
    }

    private AgentRunLifecycle.Context publication(Prepared prepared) {
        return new AgentRunLifecycle.Context(prepared.lease().fence(), prepared.sourcesPayloadJson(), prepared.routerPayloadJson());
    }

    private void persistProgress(Prepared p, LoopState st) {
        lifecycle.progress(publication(p), st.progress(), st.assistantTexts);
        if (st.totalTokens() > TOKEN_HARD_CAP) {
            throw new BusinessException(500, "超出 Token 预算上限,运行中止");
        }
    }

    // ===================== 工具执行/审批闸 =====================

    private void runAgentScope(Prepared p, FluxSink<String> sink, LoopState st, AgentRun run) {
        AgentRunBudget budget = AgentRunBudget.from(run);
        budget.requireAvailable();
        int baseInput = run.getPromptTokens() == null ? 0 : run.getPromptTokens();
        int baseOutput = run.getCompletionTokens() == null ? 0 : run.getCompletionTokens();
        st.carriedTokens = Math.toIntExact(budget.usedTokens() - baseInput - baseOutput);
        List<AgentRuntimePort.ResumeResult> results = p.suppressStart()
                ? agentActionService.listByRun(run.getId()).stream()
                    .filter(action -> action.getToolCallId() != null)
                    .map(action -> new AgentRuntimePort.ResumeResult(action.getToolCallId(),
                            p.tools().stream().filter(tool -> tool.getId().equals(action.getToolId()))
                                    .map(AgentTool::getCode).findFirst()
                                    .orElseThrow(() -> new BusinessException(409, "原业务工具已不可用，无法续跑")),
                            writeJson(Map.of("status", action.getStatus(), "result",
                                    action.getResultJson() == null ? "" : action.getResultJson(),
                                    "error", action.getErrorMessage() == null ? "" : action.getErrorMessage()))))
                    .toList() : List.of();
        AgentRuntimePort.Result result = agentScopeRuntime.execute(new AgentRuntimePort.Request(
                p.runKey(), p.userId(), p.system(), p.question(), modelConfigService.getById(p.modelConfigId()),
                p.tools(), budget.remainingSteps(), results,
                (tool, callId, arguments) -> {
                    // The platform gateway is the sole approval authority; the SDK never invokes HTTP directly.
                    synchronized (st) {
                        p.lease().requireAlive();
                        if (!st.pending.isEmpty()) {
                            throw new BusinessException(409, "运行已等待审批，不再执行新的工具调用");
                        }
                        long start = System.currentTimeMillis();
                        AgentStep step = runStateService.appendStep(p.lease().fence(), p.runId(), AgentStep.STEP_TYPE_TOOL,
                                tool.getCode(), null, writeJson(arguments), null, "RUNNING", null, null);
                        emitEvent(sink, Map.of("type", "tool_call", "runId", p.runKey(),
                                "toolCode", tool.getCode(), "toolName", tool.getName(), "status", "RUNNING"));
                        AgentAction action = agentActionService.request(new AgentActionService.ActionCommand(
                                tool.getId(), arguments, null, ToolInvocationKey.create(p.runKey(), tool.getCode(), arguments),
                                null, null, p.runId(), step.getId(), null, callId, p.lease().fence()), p.userId());
                        String value = finishToolStep(p, st, sink, tool, step, action, start);
                        return new AgentRuntimePort.ToolReply(value,
                                AgentActionState.blocksContinuation(action.getStatus()));
                    }
                }, delta -> {
                    p.lease().requireAlive();
                    emitEvent(sink, Map.of("type", "content", "content", delta));
                }, p.lease().fence(), p.lease()::requireAlive, budget.remainingTokens(), usage -> {
                    synchronized (st) {
                        st.stepCount = Math.addExact(budget.usedSteps(), usage.modelCalls());
                        st.promptTokens = Math.addExact(baseInput, usage.inputTokens());
                        st.completionTokens = Math.addExact(baseOutput, usage.outputTokens());
                        lifecycle.progress(publication(p), st.progress(), null);
                    }
                }));
        st.stepCount = (run.getStepCount() == null ? 0 : run.getStepCount()) + result.modelCalls();
        st.promptTokens = (run.getPromptTokens() == null ? 0 : run.getPromptTokens()) + result.inputTokens();
        st.completionTokens = (run.getCompletionTokens() == null ? 0 : run.getCompletionTokens()) + result.outputTokens();
        runStateService.appendStep(p.lease().fence(), p.runId(), AgentStep.STEP_TYPE_LLM, null, null,
                null, result.answer(), result.status().name(), null, result.inputTokens() + result.outputTokens());
        if (result.status() == AgentRuntimePort.Status.SUSPENDED && !st.pending.isEmpty()) {
            pauseForApproval(p, sink, st, null);
        } else if (result.status() == AgentRuntimePort.Status.COMPLETED
                && result.answer() != null && !result.answer().isBlank()) {
            completeRun(p, sink, st, result.answer(), true);
        } else {
            throw new BusinessException(409, "Agent 运行未完成: " + result.status());
        }
    }

    /**
     * 工具执行器(AgentGate):幂等 → AgentActionService.request(自动放行/待审)→ 轨迹与事件。
     * 需审批时返回 __agent_pause__ 标记,由模型停止调用并在整轮结束后进入 WAITING_APPROVAL。
     */
    private AgentToolCallback.Executor executorFor(Prepared p, LoopState st, FluxSink<String> sink) {
        return (tool, args, argsJson) -> {
            synchronized (st) {
                p.lease().requireAlive();
                if (!st.pending.isEmpty()) return writeJson(Map.of("__agent_pause__", true, "message", "已有业务动作挂起，请等待处理完成"));
            long start = System.currentTimeMillis();
            String key = idemKey(p.runKey(), tool.getCode(), argsJson);
            Long actor = p.userId();
            AgentStep step = runStateService.appendStep(p.lease().fence(), p.runId(), AgentStep.STEP_TYPE_TOOL,
                    tool.getCode(), null, argsJson, null, "RUNNING", null, null);

            emitEvent(sink, Map.of("type", "tool_call", "runId", p.runKey(),
                    "toolCode", tool.getCode(), "toolName", tool.getName(),
                    "argsPreview", preview(argsJson, ARGS_PREVIEW_CAP), "status", "RUNNING"));

            try {
                AgentAction existing = agentActionService.findIdempotent(tool.getId(), actor, key);
                if (existing != null) {
                    return finishToolStep(p, st, sink, tool, step, existing, start);
                }
                AgentAction created = agentActionService.request(new AgentActionService.ActionCommand(
                        tool.getId(), args, null, key, null, null, p.runId(), step.getId(), null, null, p.lease().fence()), actor);
                return finishToolStep(p, st, sink, tool, step, created, start);
            } catch (Exception e) {
                String message = e instanceof BusinessException be ? be.getMessage()
                        : String.valueOf(e.getMessage());
                finishToolStepError(p, sink, tool, step, message, start);
                return "{\"__agent_error__\":true,\"message\":\""
                        + sanitize(message, 300) + "\"}";
            }
            }
        };
    }

    private String finishToolStep(Prepared p, LoopState st, FluxSink<String> sink,
                                  AgentTool tool, AgentStep step, AgentAction action, long start) {
        String status = action.getStatus();
        int latency = (int) (System.currentTimeMillis() - start);
        updateStep(p, step, status, action, latency);

        if (AgentActionState.blocksContinuation(status)) {
            st.addPending(new PendingInfo(action, tool.getCode(), tool.getName()));
            emitEvent(sink, Map.of("type", "tool_result", "runId", p.runKey(),
                    "toolCode", tool.getCode(), "status", status, "actionId", action.getId(),
                    "latencyMs", latency));
            String reason = "PENDING_APPROVAL".equals(status) ? "人工审批" : "UNKNOWN".equals(status) ? "业务结果核对" : "后台执行";
            return writeJson(Map.of("__agent_pause__", true, "actionId", action.getId(),
                    "message", "该操作正在等待" + reason + "，请停止调用工具，不得虚构结果。"));
        }
        if ("SUCCEEDED".equals(status)) {
            String result = action.getResultJson();
            emitEvent(sink, Map.of("type", "tool_result", "runId", p.runKey(),
                    "toolCode", tool.getCode(), "status", status, "actionId", action.getId(),
                    "result", result == null ? "" : truncate(result, RESULT_TO_LLM_CAP),
                    "latencyMs", latency));
            return result == null || result.isBlank() ? "{\"status\":\"SUCCEEDED\",\"message\":\"操作已完成(无返回内容)\"}"
                    : truncate(result, RESULT_TO_LLM_CAP);
        }
        if ("REJECTED".equals(status)) {
            emitEvent(sink, Map.of("type", "tool_result", "runId", p.runKey(),
                    "toolCode", tool.getCode(), "status", status, "actionId", action.getId(),
                    "latencyMs", latency));
            return "{\"__agent_rejected__\":true,\"actionId\":" + action.getId()
                    + ",\"reason\":\"" + sanitize(action.getErrorMessage(), 300) + "\"}";
        }
        // FAILED / EXECUTING / READY 兜底
        String error = action.getErrorMessage() == null ? status : action.getErrorMessage();
        emitEvent(sink, Map.of("type", "tool_result", "runId", p.runKey(),
                "toolCode", tool.getCode(), "status", status, "actionId", action.getId(),
                "error", sanitize(error, 500), "latencyMs", latency));
        return "{\"__agent_error__\":true,\"actionId\":" + action.getId()
                + ",\"message\":\"" + sanitize(error, 300) + "\"}";
    }

    private void finishToolStepError(Prepared p, FluxSink<String> sink, AgentTool tool,
                                     AgentStep step, String message, long start) {
        int latency = (int) (System.currentTimeMillis() - start);
        updateStep(p, step, "FAILED", message, latency);
        emitEvent(sink, Map.of("type", "tool_result", "runId", step.getRunId(),
                "toolCode", tool.getCode(), "status", "FAILED",
                "error", sanitize(message, 500), "latencyMs", latency));
    }

    private void updateStep(Prepared p, AgentStep step, String status, Object output, int latency) {
        step.setStatus(status);
        step.setOutputJson(output instanceof String s ? truncate(s, 256 * 1024)
                : output == null ? null : String.valueOf(output));
        step.setLatencyMs(latency);
        runStateService.updateStep(p.lease().fence(), step);
    }

    // ===================== 事件与 JSON 辅助 =====================

    private void emit(FluxSink<String> sink, String json) {
        if (sink.isCancelled()) return;
        try {
            sink.next(json);
        } catch (Throwable t) {
            log.warn("SSE 发送失败: {}", t.getMessage());
        }
    }

    private void emitEvent(FluxSink<String> sink, Map<String, Object> event) {
        emit(sink, writeJson(event));
    }

    private void emitContent(FluxSink<String> sink, String text) {
        for (String chunk : chunkText(text, 120)) {
            Map<String, Object> evt = new LinkedHashMap<>();
            evt.put("type", "content");
            evt.put("content", chunk);
            emit(sink, writeJson(evt));
        }
    }

    private String errorEvent(String message) {
        return "{\"type\":\"error\",\"message\":\"" + sanitize(message, 500) + "\"}";
    }

    private String writeJson(Object value) {
        return JSON.toJSONString(value);
    }

    private String sanitize(String value, int max) {
        if (value == null) return "";
        String cleaned = value.replace("\"", "'").replace("\n", " ").replace("\r", " ");
        return truncate(cleaned, max);
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }

    private String preview(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return truncate(value, max);
    }

    private List<String> chunkText(String text, int size) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;
        for (int i = 0; i < text.length(); i += size) {
            chunks.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return chunks;
    }

    // ===================== 提示词与选项 =====================

    private List<Message> buildMessages(String system, String question, List<String> assistantTexts,
                                        String resumeNote) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(system));
        messages.add(new UserMessage(question));
        for (String text : assistantTexts) {
            if (text != null && !text.isBlank()) {
                messages.add(new AssistantMessage(text));
            }
        }
        if (resumeNote != null && !resumeNote.isBlank()) {
            messages.add(new UserMessage(resumeNote));
        }
        return messages;
    }

    private ChatOptions runtimeOptions(ModelProvider provider, List<ToolCallback> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) {
            // 无可用工具:不强开 tool-calling,等价普通 call
            return null;
        }
        if (provider == ModelProvider.DASHSCOPE) {
            return DashScopeChatOptions.builder()
                    .toolCallbacks(callbacks)
                    .internalToolExecutionEnabled(true)
                    .build();
        }
        return OpenAiChatOptions.builder()
                .toolCallbacks(callbacks)
                .internalToolExecutionEnabled(true)
                .build();
    }

    // ===================== 杂项 =====================

    private int resolveMaxSteps(AgentAskConfig agentCfg) {
        Integer steps = agentCfg == null ? null : agentCfg.getMaxSteps();
        if (steps == null || steps <= 0) {
            return DEFAULT_MAX_STEPS;
        }
        return Math.min(steps, MAX_STEPS_LIMIT);
    }

    private Map<String, Object> routerPayload(IntentRouterResponse route) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("used", true);
        payload.put("needRetrieval", route.isNeedRetrieval());
        payload.put("reason", route.getReason());
        if (route.getRecommendedBases() != null && !route.getRecommendedBases().isEmpty()) {
            IntentRouterResponse.KnowledgeBaseScore selected = route.getRecommendedBases().get(0);
            payload.put("selectedKnowledgeBaseId", selected.getKnowledgeBaseId());
            payload.put("selectedKnowledgeBaseName", selected.getKnowledgeBaseName());
            payload.put("candidates", route.getRecommendedBases());
        }
        return payload;
    }

    private String idemKey(String runKey, String toolCode, String argsJson) {
        return runKey + "::" + toolCode + "::" + sha1(argsJson == null ? "{}" : argsJson).substring(0, 12);
    }

    private String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf(value.hashCode());
        }
    }

    private int[] readUsage(ChatResponse response) {
        int prompt = 0;
        int completion = 0;
        try {
            Object usageObj = response.getMetadata().get("usage");
            if (usageObj instanceof Usage usage) {
                if (usage.getPromptTokens() != null) prompt = usage.getPromptTokens();
                if (usage.getCompletionTokens() != null) completion = usage.getCompletionTokens();
            } else if (usageObj instanceof Map<?, ?> map) {
                prompt = intOf(map.get("promptTokens"), map.get("prompt_tokens"));
                completion = intOf(map.get("completionTokens"), map.get("completion_tokens"));
            }
        } catch (Exception e) {
            log.debug("读取 token 用量失败(不影响运行): {}", e.getMessage());
        }
        return new int[]{prompt, completion};
    }

    private int intOf(Object first, Object second) {
        Object value = first != null ? first : second;
        return value instanceof Number n ? n.intValue() : 0;
    }

    // ===================== 内部数据 =====================

    /** 预备产物:所有运行期不变信息(含 run/history 落库) */
    public record Prepared(String runKey, Long runId, Long historyId, String sessionId, String question,
                           Long userId, Long kbId, Long modelConfigId, ChatModel model, ModelProvider provider,
                           String system, boolean memoryEnabled, String sourcesEvent, String sourcesPayloadJson,
                           String routerEventJson, String routerPayloadJson, String evidenceText,
                           List<AgentTool> tools, int maxSteps, boolean suppressStart,
                           String resumeNote, RunExecutionLeaseService.Lease lease) {
        Prepared withLease(RunExecutionLeaseService.Lease active) {
            return new Prepared(runKey, runId, historyId, sessionId, question, userId, kbId, modelConfigId,
                    model, provider, system, memoryEnabled, sourcesEvent, sourcesPayloadJson,
                    routerEventJson, routerPayloadJson, evidenceText, tools, maxSteps, suppressStart, resumeNote, active);
        }
    }

    private record PendingInfo(AgentAction action, String toolCode, String toolName) {
    }

    /** 决策循环可变状态(单线程执行,无并发) */
    private static final class LoopState {
        private final List<PendingInfo> pending = new ArrayList<>();
        private final List<String> assistantTexts = new ArrayList<>();
        private int stepCount = 0;
        private int promptTokens = 0;
        private int completionTokens = 0;
        private int carriedTokens = 0;
        private AgentRunLifecycle.Progress progress() {
            return new AgentRunLifecycle.Progress(stepCount, promptTokens, completionTokens, totalTokens());
        }
        private int totalTokens() { return Math.addExact(carriedTokens, Math.addExact(promptTokens, completionTokens)); }

        private void addPending(PendingInfo info) {
            for (PendingInfo existing : pending) {
                if (existing.action().getId().equals(info.action().getId())) {
                    return;
                }
            }
            pending.add(info);
        }
    }
}
