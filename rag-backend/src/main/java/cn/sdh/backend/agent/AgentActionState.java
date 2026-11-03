package cn.sdh.backend.agent;

/** Shared continuation semantics. Unknown values fail closed. */
public enum AgentActionState {
    PENDING_APPROVAL, READY, EXECUTING, UNKNOWN, SUCCEEDED, FAILED, REJECTED;

    public static boolean isTerminal(String status) {
        return SUCCEEDED.name().equals(status) || FAILED.name().equals(status) || REJECTED.name().equals(status);
    }
    public static boolean blocksContinuation(String status) { return !isTerminal(status); }
}
