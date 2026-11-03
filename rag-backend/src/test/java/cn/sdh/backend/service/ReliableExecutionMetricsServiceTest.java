package cn.sdh.backend.service;

import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.mapper.PlatformJobMapper;
import cn.sdh.backend.mapper.TransactionalOutboxMapper;
import cn.sdh.backend.mapper.WebhookDeliveryMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReliableExecutionMetricsServiceTest {
    @Mock PlatformJobMapper jobMapper;
    @Mock TransactionalOutboxMapper outboxMapper;
    @Mock WebhookDeliveryMapper deliveryMapper;

    @Test
    void persistentSnapshotCalculatesRatesAndActiveAlerts() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReliableExecutionMetricsService service = new ReliableExecutionMetricsService(
                jobMapper, outboxMapper, deliveryMapper, registry);
        ReflectionTestUtils.setField(service, "jobBacklogThreshold", 10L);
        ReflectionTestUtils.setField(service, "oldestJobSecondsThreshold", 300L);
        ReflectionTestUtils.setField(service, "outboxBacklogThreshold", 20L);
        ReflectionTestUtils.setField(service, "deadLetters24hThreshold", 1L);
        service.registerGauges();
        when(jobMapper.selectCount(any())).thenReturn(8L, 4L, 2L, 3L, 20L, 18L, 2L, 5L);
        when(outboxMapper.selectCount(any())).thenReturn(1L, 0L);
        when(deliveryMapper.selectCount(any())).thenReturn(2L, 1L);
        PlatformJob oldest = new PlatformJob();
        oldest.setCreateTime(LocalDateTime.now().minusSeconds(600));
        when(jobMapper.selectOne(any())).thenReturn(oldest);

        service.refresh();

        ReliableExecutionMetricsService.Snapshot snapshot = service.current();
        assertEquals(12L, snapshot.pendingJobs() + snapshot.retryWaitJobs());
        assertEquals(90D, snapshot.successRate24h());
        assertTrue(snapshot.alerts().stream().anyMatch(alert -> "JOB_BACKLOG".equals(alert.code())));
        assertTrue(snapshot.alerts().stream().anyMatch(alert -> "JOB_QUEUE_AGE".equals(alert.code())));
        assertTrue(snapshot.alerts().stream().anyMatch(alert -> "WEBHOOK_DEAD_LETTER".equals(alert.code())));
        assertEquals(12D, registry.get("sdh.jobs.count").tag("state", "backlog").gauge().value());
    }
}
