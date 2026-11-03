package cn.sdh.backend.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentRunRecoveryPolicyTest {
    @Test void onlyFencedInterruptedOrFailedRunsWithoutBusinessActionsCanRestart() {
        assertTrue(decide("INTERRUPTED", 1, false, "agentscope", false).allowed());
        assertTrue(decide("FAILED", 3, false, "legacy", false).allowed());
        for (String state : java.util.List.of("RUNNING", "WAITING_ACTION", "WAITING_APPROVAL", "COMPLETED", "REJECTED", "CANCELLED", "RECOVERY_QUEUED")) {
            assertFalse(decide(state, 1, false, "agentscope", false).allowed());
        }
        assertFalse(decide("INTERRUPTED", 0, false, "agentscope", false).allowed());
        assertFalse(decide("INTERRUPTED", 1, true, "agentscope", false).allowed());
        assertFalse(decide("INTERRUPTED", 1, false, "unknown", false).allowed());
        assertFalse(decide("INTERRUPTED", 1, false, "agentscope", true).allowed());
    }

    @Test void existingActionFactsAreNeverSilentlyDiscardedEvenWhenTheySucceeded() {
        var decision = decide("FAILED", 2, false, "agentscope", true);
        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("业务动作"));
    }

    private AgentRunRecoveryPolicy.Decision decide(String status, long epoch, boolean leased, String engine, boolean actions) {
        return AgentRunRecoveryPolicy.decide(new AgentRunRecoveryPolicy.Facts(status, epoch, leased, engine, actions));
    }
}
