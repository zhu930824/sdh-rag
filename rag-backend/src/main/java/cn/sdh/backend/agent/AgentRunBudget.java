package cn.sdh.backend.agent;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.AgentRun;

/** Persisted cumulative usage is shared by resume, recovery and both execution engines. */
public record AgentRunBudget(int usedSteps, int maxSteps, long usedTokens) {
    public static final int DEFAULT_MAX_STEPS = 8;
    public static final int TOKEN_LIMIT = 200_000;

    public AgentRunBudget {
        if (usedSteps < 0 || maxSteps <= 0 || usedTokens < 0) {
            throw new BusinessException(409, "运行预算记录不合法，无法安全继续");
        }
    }

    public static AgentRunBudget from(AgentRun run) {
        return from(run.getStepCount(), run.getMaxSteps(), run.getTotalTokens(), run.getPromptTokens(), run.getCompletionTokens());
    }

    public static AgentRunBudget from(Integer steps, Integer maxSteps, Integer total, Integer input, Integer output) {
        int limit = maxSteps == null ? DEFAULT_MAX_STEPS : maxSteps;
        if (negative(steps) || negative(total) || negative(input) || negative(output) || limit <= 0) {
            throw new BusinessException(409, "运行预算记录不合法，无法安全继续");
        }
        long measured = (long) value(input) + value(output);
        return new AgentRunBudget(value(steps), limit, Math.max(value(total), measured));
    }

    public int remainingSteps() { return Math.max(0, maxSteps - usedSteps); }
    public long remainingTokens() { return Math.max(0, TOKEN_LIMIT - usedTokens); }
    public boolean exhausted() { return remainingSteps() == 0 || remainingTokens() == 0; }
    public void requireAvailable() {
        if (exhausted()) throw new BusinessException(409, "运行已达到步骤或 Token 预算，无法继续");
    }
    private static boolean negative(Integer number) { return number != null && number < 0; }
    private static int value(Integer number) { return number == null ? 0 : number; }
}
