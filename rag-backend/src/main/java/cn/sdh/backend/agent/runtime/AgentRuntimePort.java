package cn.sdh.backend.agent.runtime;

import cn.sdh.backend.entity.AgentTool;
import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.agent.RunExecutionLeaseService;
import cn.sdh.backend.agent.AgentRunBudget;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Application-owned runtime contract; no SDK message types cross this boundary. */
public interface AgentRuntimePort {
    record ToolReply(String value, boolean suspended) {}
    record ResumeResult(String toolCallId, String toolCode, String value) {}
    record Usage(int modelCalls, int inputTokens, int outputTokens) {}
    @FunctionalInterface interface ToolExecutor {
        ToolReply execute(AgentTool tool, String toolCallId, Map<String, Object> arguments);
    }
    record Request(String runKey, Long userId, String systemPrompt, String question,
                   ModelConfig model, List<AgentTool> tools, int maxSteps,
                   List<ResumeResult> resumeResults, ToolExecutor executor, Consumer<String> textDelta,
                   RunExecutionLeaseService.Fence fence, Runnable checkpoint,
                   long remainingTokens, Consumer<Usage> usageObserver) {
        public Request(String runKey, Long userId, String systemPrompt, String question,
                       ModelConfig model, List<AgentTool> tools, int maxSteps,
                       List<ResumeResult> resumeResults, ToolExecutor executor, Consumer<String> textDelta,
                       RunExecutionLeaseService.Fence fence, Runnable checkpoint) {
            this(runKey, userId, systemPrompt, question, model, tools, maxSteps, resumeResults, executor, textDelta,
                    fence, checkpoint, AgentRunBudget.TOKEN_LIMIT, usage -> {});
        }
        public Request(String runKey, Long userId, String systemPrompt, String question,
                       ModelConfig model, List<AgentTool> tools, int maxSteps,
                       List<ResumeResult> resumeResults, ToolExecutor executor, Consumer<String> textDelta) {
            this(runKey, userId, systemPrompt, question, model, tools, maxSteps, resumeResults, executor, textDelta, null, () -> {});
        }
    }
    enum Status { COMPLETED, SUSPENDED, LIMIT_REACHED, INTERRUPTED }
    record Result(Status status, String answer, int modelCalls, int inputTokens, int outputTokens) {}
    Result execute(Request request);
}
