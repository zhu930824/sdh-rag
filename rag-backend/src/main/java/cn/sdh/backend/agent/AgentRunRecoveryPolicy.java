package cn.sdh.backend.agent;

/** Conservative restart policy. Action status alone cannot prove that replaying a model is safe. */
public final class AgentRunRecoveryPolicy {
    private AgentRunRecoveryPolicy() {}
    public record Facts(String status, long epoch, boolean leased, String engine, boolean hasActions, AgentRunBudget budget) {
        public Facts(String status, long epoch, boolean leased, String engine, boolean hasActions) {
            this(status, epoch, leased, engine, hasActions, AgentRunBudget.from(null, null, null, null, null));
        }
    }
    public record Decision(boolean allowed, String reason) {}

    public static Decision decide(Facts facts) {
        if (!"INTERRUPTED".equals(facts.status()) && !"FAILED".equals(facts.status())) {
            return blocked("仅中断或失败的运行可申请恢复");
        }
        if (facts.epoch() <= 0 || facts.leased()) return blocked("运行未受执行租约保护，或执行权尚未释放");
        if (!"legacy".equals(facts.engine()) && !"agentscope".equals(facts.engine())) return blocked("原运行引擎不受支持");
        if (facts.hasActions()) return blocked("运行已有业务动作，请先核对动作结果；当前不支持从该断点自动恢复");
        if (facts.budget().exhausted()) return blocked("运行已达到步骤或 Token 预算，无法恢复");
        return new Decision(true, "未登记业务动作，可保留原问题和证据重新执行；模型调用可能产生额外费用");
    }
    private static Decision blocked(String reason) { return new Decision(false, reason); }
}
