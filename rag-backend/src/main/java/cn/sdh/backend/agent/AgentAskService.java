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
import cn.sdh.backend.service.factory.ChatModelFactory;
import cn.sdh.backend.service.factory.ModelProvider;
import cn.sdh.backend.service.impl.ChatServiceImpl;
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
 * 聊天内 Agent 决策循环(方案 A:DashScope/OpenAI 模型内部工具循环 + 审批闸截停)。
 * <p>
 * 决策轮全部用非流式 call()(规避 dashscope 流式 tool-call 兼容风险);内部工具循环由模型侧驱动,
 * 工具回调(AgentToolCallback)内经审批闸执行:自动放行返回真实结果;需审批返回 __agent_pause__
 * 标记并记入 pending,决策循环在整轮结束后把 run 置 WAITING_APPROVAL 并推送审批事件。
 * 最终回答以整包切片方式走 content 事件(前端打字机效果不变)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAskService {

    private static final int DEFAULT_MAX_STEPS = 8;
    private static final int MAX_STEPS_LIMIT = 20;
    private static final int EVIDENCE_CAP = 12 * 1024;
    private static final int ROUND_TEXT_CAP = 20 * 1024;
    private static final int RESULT_TO_LLM_CAP = 6 * 1024;
    private static final int ARGS_PREVIEW_CAP = 2 * 1024;
    private static final int TOKEN_HARD_CAP = 200_000;

    private final ChatServiceImpl chatService;
    private final AgentActionService agentActionService;
    private final AgentToolService agentToolService;
    private final RunStateService runStateService;
    private final ChatModelFactory chatModelFactory;
    private final ModelConfigService modelConfigService;
    private final IntentRouterService intentRouterService;
    private final IntentRouterConfig intentRouterConfig;
    private final AgentCapability capability;

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
        return Flux.<String>create(sink -> runLoop(prepared, sink), FluxSink.OverflowStrategy.BUFFER)
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ===================== 预备 =====================

    private Prepared prepare(String question, String sessionId, Long userId, Long knowledgeId, Long modelId,
                             boolean memoryEnabled, Map<String, Object> metadataFilters, int maxSteps) {
        String currentSessionId = (sessionId != null && !sessionId.isEmpty())
                ? sessionId : UUID.randomUUID().toString();

        ModelConfig modelConfig = modelId == null
                ? modelConfigService.getDefault()
                : modelConfigService.getById(modelId);
        if (modelConfig == null) {
            throw new BusinessException(400, "模型配置错误,请检查模型设置");
        }
        ModelProvider provider = ModelProvider.fromString(modelConfig.getProvider());
        if (!capability.supports(provider)) {
            throw new BusinessException(400, "当前模型供应商(" + modelConfig.getProvider()
                    + ")暂不支持 Agent 工具调用:请在模型管理中选择白名单供应商(默认 DashScope/OpenAI)"
                    + "或关闭 Agent 模式");
        }
        ChatModel model = chatModelFactory.getModelById(modelConfig.getModelId());
        if (model == null) {
            model = chatModelFactory.getModel(null);
        }
        if (model == null) {
            throw new BusinessException(400, "无法创建聊天模型,请检查模型配置");
        }

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
        runStateService.bindHistoryId(run.getId(), historyId);

        String base = buildSystemPrompt(evidence, tools);
        String system = memoryEnabled
                ? chatService.memoryPrompt(base, userId, currentSessionId, question, true)
                : base;

        AgentRun persisted = run;
        persisted.setEvidenceText(evidence == null || evidence.isBlank() ? null : truncate(evidence, EVIDENCE_CAP));
        runStateService.save(persisted);

        return new Prepared(run.getRunKey(), run.getId(), historyId, currentSessionId, question, userId,
                effectiveKb, modelConfig.getId(), model, provider, system, memoryEnabled,
                sourcesEvent, sourcesPayloadJson, routerEventJson, routerPayloadJson, evidence, tools, maxSteps,
                false, null);
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
        if (!"WAITING_APPROVAL".equals(run.getStatus())) {
            throw new BusinessException(409, "运行状态不是等待审批(当前:" + run.getStatus()
                    + "),请刷新页面获取最新状态");
        }
        if (!runStateService.transition(runKey, AgentRun.STATUS_WAITING_APPROVAL, AgentRun.STATUS_RUNNING)) {
            throw new BusinessException(409, "运行已在续跑或状态已变化,请刷新页面");
        }
        Prepared prepared = preparedFromRun(run, decided);
        return execute(prepared);
    }

    /** 后台续跑(审批中心 approve 缺省路径):fire-and-forget,终态落 chat_history */
    public void resumeInBackground(String runKey, Long decidedActionId) {
        try {
            AgentRun run = runStateService.getByRunKey(runKey);
            if (run == null) return;
            resumeFlux(runKey, run.getUserId(), decidedActionId)
                    .subscribe(value -> {
                    }, e -> log.warn("后台续跑失败 runKey={}: {}", runKey, e.getMessage()));
        } catch (Exception e) {
            log.warn("后台续跑启动失败 runKey={}: {}", runKey, e.getMessage());
        }
    }

    /** 拒绝路径(确定性收尾,不回调 LLM):写入最终文本与状态 */
    public void finalizeRejected(AgentAction action) {
        if (action == null || action.getRunId() == null) return;
        try {
            AgentRun row = runStateService.getById(action.getRunId());
            if (row == null || !AgentRun.STATUS_WAITING_APPROVAL.equals(row.getStatus())) return;
            if (!runStateService.transition(row.getRunKey(), AgentRun.STATUS_WAITING_APPROVAL,
                    AgentRun.STATUS_REJECTED)) return;
            row.setStatus(AgentRun.STATUS_REJECTED);
            row.setCompletedAt(java.time.LocalDateTime.now());
            runStateService.save(row);

            String reason = action.getErrorMessage() == null ? "审批人拒绝" : action.getErrorMessage();
            String answer = "业务动作「" + action.getToolName() + "」已被拒绝(" + reason + "),本次未执行。";
            chatService.persistAnswerRow(row.getHistoryId(), answer, row.getKnowledgeId(), row.getModelId(),
                    null, null, row.getRunKey(), buildTraceJson(row));
            log.info("Agent 运行被拒绝并收尾 runKey={}", row.getRunKey());
        } catch (Exception e) {
            log.warn("Agent 拒绝收尾失败: {}", e.getMessage());
        }
    }

    private Prepared preparedFromRun(AgentRun run, AgentAction decided) {
        ModelConfig modelConfig = run.getModelId() == null
                ? modelConfigService.getDefault()
                : modelConfigService.getById(run.getModelId());
        if (modelConfig == null) {
            throw new BusinessException(400, "运行关联的模型配置已不存在,无法续跑");
        }
        ModelProvider provider = ModelProvider.fromString(modelConfig.getProvider());
        if (!capability.supports(provider)) {
            throw new BusinessException(400, "当前模型供应商(" + modelConfig.getProvider()
                    + ")已不在 Agent 工具调用白名单,无法续跑");
        }
        ChatModel model = chatModelFactory.getModelById(modelConfig.getModelId());
        if (model == null) {
            model = chatModelFactory.getModel(null);
        }
        if (model == null) {
            throw new BusinessException(400, "无法创建聊天模型,无法续跑");
        }
        List<AgentTool> tools = agentToolService.listUsable(run.getUserId());
        String base = buildSystemPrompt(run.getEvidenceText(), tools);
        String system = chatService.memoryPrompt(base, run.getUserId(), run.getSessionId(),
                run.getQuestion(), true);

        String note = approvalNote(decided);
        // 续跑不重放 routerInfo/sources;suppressStart=true
        return new Prepared(run.getRunKey(), run.getId(), run.getHistoryId(), run.getSessionId(),
                run.getQuestion(), run.getUserId(), run.getKnowledgeId(), modelConfig.getId(),
                model, provider, system, true, null, null, null, null, run.getEvidenceText(),
                tools, run.getMaxSteps() == null ? DEFAULT_MAX_STEPS : run.getMaxSteps(),
                true, note);
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

            int turn = 0;
            for (turn = 1; turn <= p.maxSteps(); turn++) {
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
                    throw new BusinessException(500, "模型调用失败: " + e.getMessage());
                }
                int latency = (int) (System.currentTimeMillis() - llmStart);

                String text = response.getResult() == null ? null : response.getResult().getOutput().getText();
                int[] usage = readUsage(response);
                st.promptTokens += usage[0];
                st.completionTokens += usage[1];

                if (text != null && !text.isBlank()) {
                    st.assistantTexts.add(truncate(text, ROUND_TEXT_CAP));
                }
                st.stepCount = turn;
                runStateService.appendStep(run.getId(), AgentStep.STEP_TYPE_LLM, null, null,
                        null, truncate(text, ROUND_TEXT_CAP), "SUCCEEDED", latency,
                        usage[0] + usage[1]);
                persistProgress(run, st);

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
            failRun(p, sink, e.getMessage());
        } finally {
            if (sink != null && !sink.isCancelled()) {
                sink.complete();
            }
        }
    }

    /** 审批暂停收尾:状态落库 + 事件推送(流正常结束,无 done 事件) */
    private void pauseForApproval(Prepared p, FluxSink<String> sink, LoopState st, String note) {
        if (note != null && !note.isBlank()) {
            emitContent(sink, note);
        }
        AgentAction first = st.pending.get(0).action();
        List<Long> pendingIds = st.pending.stream().map(pi -> pi.action().getId()).toList();

        AgentRun run = runStateService.getByRunKey(p.runKey());
        run.setStatus(AgentRun.STATUS_WAITING_APPROVAL);
        run.setPendingActionId(first.getId());
        run.setStepCount(st.stepCount);
        run.setPromptTokens(st.promptTokens);
        run.setCompletionTokens(st.completionTokens);
        run.setTotalTokens(st.promptTokens + st.completionTokens);
        runStateService.save(run);

        emitEvent(sink, Map.of("type", "agent_status", "status", AgentRun.STATUS_WAITING_APPROVAL,
                "pendingActionIds", pendingIds));
        for (PendingInfo info : st.pending) {
            AgentAction action = info.action();
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

    /** 正常完成:内容切片下发 → 全字段落库 → 记忆写入 → done */
    private void completeRun(Prepared p, FluxSink<String> sink, LoopState st, String answer) {
        AgentRun run = runStateService.getByRunKey(p.runKey());
        run.setStatus(AgentRun.STATUS_COMPLETED);
        run.setStepCount(st.stepCount);
        run.setPromptTokens(st.promptTokens);
        run.setCompletionTokens(st.completionTokens);
        run.setTotalTokens(st.promptTokens + st.completionTokens);
        run.setCompletedAt(java.time.LocalDateTime.now());
        runStateService.save(run);

        emitContent(sink, answer);
        String traceJson = buildTraceJson(run);

        chatService.persistAnswerRow(p.historyId(), answer, p.kbId(), p.modelConfigId(),
                p.sourcesPayloadJson(), p.routerPayloadJson(), p.runKey(), traceJson);
        if (p.memoryEnabled()) {
            chatService.storeMemoryAfterTurn(p.userId(), p.sessionId(), p.question(), answer, true);
        }
        emit(sink, "{\"type\":\"done\",\"historyId\":" + p.historyId()
                + ",\"runId\":\"" + p.runKey() + "\"}");
        log.info("Agent 运行完成 runKey={}, steps={}, tokens={}",
                p.runKey(), st.stepCount, run.getTotalTokens());
    }

    /** 失败收尾:run=FAILED + error 事件 */
    private void failRun(Prepared p, FluxSink<String> sink, String message) {
        try {
            AgentRun run = runStateService.getByRunKey(p.runKey());
            if (run != null) {
                run.setStatus(AgentRun.STATUS_FAILED);
                run.setErrorMessage(truncate(message, 1000));
                run.setCompletedAt(java.time.LocalDateTime.now());
                runStateService.save(run);
            }
        } catch (Exception logError) {
            log.warn("Agent 失败状态落库异常: {}", logError.getMessage());
        }
        emit(sink, errorEvent(message));
    }

    private void persistProgress(AgentRun run, LoopState st) {
        run.setStepCount(st.stepCount);
        run.setPromptTokens(st.promptTokens);
        run.setCompletionTokens(st.completionTokens);
        run.setTotalTokens(st.promptTokens + st.completionTokens);
        runStateService.save(run);
        runStateService.persistRounds(run.getId(), st.assistantTexts);
        if (st.promptTokens + st.completionTokens > TOKEN_HARD_CAP) {
            throw new BusinessException(500, "超出 Token 预算上限,运行中止");
        }
    }

    // ===================== 工具执行/审批闸 =====================

    /**
     * 工具执行器(AgentGate):幂等 → AgentActionService.request(自动放行/待审)→ 轨迹与事件。
     * 需审批时返回 __agent_pause__ 标记,由模型停止调用并在整轮结束后进入 WAITING_APPROVAL。
     */
    private AgentToolCallback.Executor executorFor(Prepared p, LoopState st, FluxSink<String> sink) {
        return (tool, args, argsJson) -> {
            long start = System.currentTimeMillis();
            String key = idemKey(p.runKey(), tool.getCode(), argsJson);
            Long actor = p.userId();
            AgentStep step = runStateService.appendStep(p.runId(), AgentStep.STEP_TYPE_TOOL,
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
                        tool.getId(), args, null, key, null, null, p.runId(), step.getId(), null), actor);
                return finishToolStep(p, st, sink, tool, step, created, start);
            } catch (Exception e) {
                String message = e instanceof BusinessException be ? be.getMessage()
                        : String.valueOf(e.getMessage());
                finishToolStepError(sink, tool, step, message, start);
                return "{\"__agent_error__\":true,\"message\":\""
                        + sanitize(message, 300) + "\"}";
            }
        };
    }

    private String finishToolStep(Prepared p, LoopState st, FluxSink<String> sink,
                                  AgentTool tool, AgentStep step, AgentAction action, long start) {
        String status = action.getStatus();
        int latency = (int) (System.currentTimeMillis() - start);
        updateStep(step, status, action, latency);

        if ("PENDING_APPROVAL".equals(status)) {
            st.addPending(new PendingInfo(action, tool.getCode(), tool.getName()));
            emitEvent(sink, Map.of("type", "tool_result", "runId", p.runKey(),
                    "toolCode", tool.getCode(), "status", status, "actionId", action.getId(),
                    "latencyMs", latency));
            return "{\"__agent_pause__\":true,\"actionId\":" + action.getId()
                    + ",\"message\":\"该操作需要人工审批,系统已暂停。请勿再次调用工具或虚构结果,"
                    + "直接告知用户此操作正在等待人工审批。\"}";
        }
        if ("SUCCEEDED".equals(status)) {
            String result = action.getResultJson();
            emitEvent(sink, Map.of("type", "tool_result", "runId", p.runKey(),
                    "toolCode", tool.getCode(), "status", status, "actionId", action.getId(),
                    "result", preview(result, RESULT_TO_LLM_CAP),
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

    private void finishToolStepError(FluxSink<String> sink, AgentTool tool,
                                     AgentStep step, String message, long start) {
        int latency = (int) (System.currentTimeMillis() - start);
        updateStep(step, "FAILED", message, latency);
        emitEvent(sink, Map.of("type", "tool_result", "runId", step.getRunId(),
                "toolCode", tool.getCode(), "status", "FAILED",
                "error", sanitize(message, 500), "latencyMs", latency));
    }

    private void updateStep(AgentStep step, String status, Object output, int latency) {
        step.setStatus(status);
        step.setOutputJson(output instanceof String s ? truncate(s, 256 * 1024)
                : output == null ? null : String.valueOf(output));
        step.setLatencyMs(latency);
        runStateService.updateStep(step);
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

    private String buildSystemPrompt(String evidence, List<AgentTool> tools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个\"知识+行动\"型企业业务助手。你可以调用受控业务工具完成任务,并按需参考检索资料。");
        prompt.append("\n\n【可用工具】").append(tools.isEmpty()
                ? "当前没有可用业务工具,仅能回答问题。"
                : "(仅在确有必要时调用;不需要就不要调用)");
        for (AgentTool tool : tools) {
            String desc = tool.getDescription() == null || tool.getDescription().isBlank()
                    ? tool.getName() : tool.getDescription();
            prompt.append("\n- ").append(tool.getCode()).append(": ").append(desc);
        }
        prompt.append("\n\n【执行规则】");
        prompt.append("\n1. 需要真实业务数据(订单/库存/工单等)时先调用工具获取,严禁编造事实或猜测数据。");
        prompt.append("\n2. 工具返回 JSON 含 __agent_pause__ 表示\"操作已提交人工审批并暂停\":立即停止调用任何工具、不得虚构执行结果,只用一句话向用户说明正在等待审批。");
        prompt.append("\n3. 工具返回含 __agent_rejected__:如实告知用户操作已被拒绝及原因,不要重试。");
        prompt.append("\n4. 工具返回含 __agent_error__:如实告知执行失败原因;确有必要可换参数组合重试一次。");
        prompt.append("\n5. 同一工具的相同参数只调用一次;重复请求只会得到既有结果或\"已在审批中\"。");
        prompt.append("\n6. 检索资料与工具结果矛盾时,以工具返回的真实数据为准。");
        prompt.append("\n\n【回答要求】用中文回答,简洁、有条理;涉及金额/订单号/库存/状态等,必须以工具返回为准并注明依据。");
        if (evidence != null && !evidence.isBlank()) {
            prompt.append("\n\n【参考资料】\n").append(evidence);
            prompt.append("\n\n【依据规则】回答优先基于参考资料;资料未覆盖且无工具可查时如实说明不知道。");
        }
        return prompt.toString();
    }

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

    // ===================== 运行轨迹摘要 =====================

    /** 供 chat_history.agent_trace 与运行详情回放;从 step 表重建,截断防爆 */
    private String buildTraceJson(AgentRun run) {
        try {
            List<Map<String, Object>> trace = new ArrayList<>();
            for (AgentStep step : runStateService.stepsOf(run.getId())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("type", step.getStepType());
                item.put("toolCode", step.getToolCode());
                item.put("actionId", step.getActionId());
                item.put("status", step.getStatus());
                item.put("seq", step.getSeq());
                if ("tool".equals(step.getStepType())) {
                    item.put("argsPreview", preview(step.getInputJson(), ARGS_PREVIEW_CAP));
                    item.put("resultPreview", preview(step.getOutputJson(), RESULT_TO_LLM_CAP));
                }
                trace.add(item);
                if (trace.size() >= 50) break;
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("runId", run.getRunKey());
            summary.put("status", run.getStatus());
            summary.put("steps", trace);
            String json = writeJson(summary);
            return json.length() > 50 * 1024 ? json.substring(0, 50 * 1024) : json;
        } catch (Exception e) {
            log.warn("Agent 轨迹摘要生成失败: {}", e.getMessage());
            return null;
        }
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
                           String resumeNote) {
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
