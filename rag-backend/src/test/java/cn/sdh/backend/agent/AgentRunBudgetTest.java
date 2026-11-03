package cn.sdh.backend.agent;

import org.junit.jupiter.api.Test;
import cn.sdh.backend.common.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;

class AgentRunBudgetTest {
    @Test void resumeUsesTheRemainingStepsAndCumulativeTokens() {
        var budget = AgentRunBudget.from(3, 8, 150_000, 120_000, 30_000);
        assertEquals(5, budget.remainingSteps());
        assertEquals(50_000, budget.remainingTokens());
        assertFalse(budget.exhausted());
    }
    @Test void inconsistentCountersCannotCreateExtraBudgetOrOverflow() {
        var measured = AgentRunBudget.from(0, 8, 10, 150_000, 60_000);
        assertTrue(measured.exhausted());
        assertThrows(BusinessException.class, measured::requireAvailable);
        var overflow = AgentRunBudget.from(0, 8, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(4_294_967_294L, overflow.usedTokens());
        assertTrue(overflow.exhausted());
    }
    @Test void exactStepOrTokenLimitStopsFurtherCalls() {
        assertTrue(AgentRunBudget.from(8, 8, 0, 0, 0).exhausted());
        assertTrue(AgentRunBudget.from(0, 8, 200_000, 0, 0).exhausted());
        assertThrows(BusinessException.class, () -> AgentRunBudget.from(-1, 8, 0, 0, 0));
        assertEquals(8, AgentRunBudget.from(null, null, null, null, null).remainingSteps());
    }
}
