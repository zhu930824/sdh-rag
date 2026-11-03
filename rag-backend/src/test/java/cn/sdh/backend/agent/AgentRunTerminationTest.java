package cn.sdh.backend.agent;

import cn.sdh.backend.job.PlatformJobCancelledException;
import cn.sdh.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentRunTerminationTest {
    @Test void aWrappedCancellationIsStillCancelled() {
        var result = AgentRunTermination.from(new BusinessException(500, "model failed", new PlatformJobCancelledException()));
        assertEquals("CANCELLED", result.status());
        assertEquals("任务已取消", result.message());
    }
    @Test void ordinaryFailuresAndMissingMessagesRemainFailures() {
        assertEquals("FAILED", AgentRunTermination.from(new IllegalStateException("模型不可用")).status());
        assertEquals("运行执行失败", AgentRunTermination.from(new RuntimeException()).message());
    }
}
