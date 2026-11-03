package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.common.context.TraceContext;
import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.mapper.PlatformJobMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformJobServiceTest {
    @Mock PlatformJobMapper mapper;

    @Test
    void enqueueCreatesPersistentIdempotentJob() {
        PlatformJobService service = new PlatformJobService(mapper);
        when(mapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            PlatformJob job = invocation.getArgument(0);
            job.setId(42L);
            return 1;
        }).when(mapper).insert(any(PlatformJob.class));

        PlatformJob job = service.enqueue(new PlatformJobService.EnqueueCommand(
                "evaluation:7", "RAG_EVALUATION", "EVALUATION", 7L, "{}", 5, 3, 9L));

        assertEquals(42L, job.getId());
        assertEquals(PlatformJob.PENDING, job.getStatus());
        assertEquals(0, job.getAttempts());
        assertNotNull(job.getTraceId());
        verify(mapper).insert(job);
    }

    @Test
    void enqueueInheritsCurrentTrace() {
        PlatformJobService service = new PlatformJobService(mapper);
        when(mapper.selectOne(any())).thenReturn(null);
        String traceId = "0123456789abcdef0123456789abcdef";

        PlatformJob job;
        try (TraceContext.Scope ignored = TraceContext.open(traceId)) {
            job = service.enqueue(new PlatformJobService.EnqueueCommand(
                    "trace-job", "TEST", null, null, "{}", 0, 1, 9L));
        }

        assertEquals(traceId, job.getTraceId());
        assertNull(TraceContext.current());
    }

    @Test
    void claimReturnsOnlyJobsWonByAtomicCompareAndSet() {
        PlatformJobService service = new PlatformJobService(mapper);
        PlatformJob claimed = job(2L, PlatformJob.RUNNING, 1, 3, 8L);
        when(mapper.selectClaimCandidates(any(), eq(5))).thenReturn(List.of(1L, 2L));
        when(mapper.claim(eq(1L), anyString(), any(), any())).thenReturn(0);
        when(mapper.claim(eq(2L), anyString(), any(), any())).thenReturn(1);
        when(mapper.selectById(2L)).thenReturn(claimed);

        List<PlatformJob> result = service.claimAvailable("worker-a", 5);

        assertEquals(List.of(claimed), result);
        verify(mapper).cancelExpiredRequested(any());
        verify(mapper).deadLetterExpired(any());
    }

    @Test
    void failureRetriesWithBackoffBeforeDeadLetter() {
        PlatformJobService service = new PlatformJobService(mapper);
        PlatformJob retryable = job(3L, PlatformJob.RUNNING, 1, 3, 8L);
        when(mapper.selectById(3L)).thenReturn(retryable);
        when(mapper.fail(eq(3L), eq("worker-a"), anyString(), anyString(), any(), any(), any())).thenReturn(1);

        assertTrue(service.fail(3L, "worker-a", new RuntimeException("temporary")));

        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> availableAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).fail(eq(3L), eq("worker-a"), status.capture(), eq("temporary"),
                availableAt.capture(), isNull(), any());
        assertEquals(PlatformJob.RETRY_WAIT, status.getValue());
        assertTrue(availableAt.getValue().isAfter(LocalDateTime.now().minusSeconds(1)));
    }

    @Test
    void exhaustedFailureMovesToDeadLetter() {
        PlatformJobService service = new PlatformJobService(mapper);
        PlatformJob exhausted = job(4L, PlatformJob.RUNNING, 3, 3, 8L);
        when(mapper.selectById(4L)).thenReturn(exhausted);
        when(mapper.fail(eq(4L), eq("worker-a"), anyString(), anyString(), any(), any(), any())).thenReturn(1);

        assertTrue(service.fail(4L, "worker-a", new RuntimeException("permanent")));

        verify(mapper).fail(eq(4L), eq("worker-a"), eq(PlatformJob.DEAD_LETTER), eq("permanent"),
                any(), any(LocalDateTime.class), any());
    }

    @Test
    void ownerBoundaryProtectsJobDetails() {
        PlatformJobService service = new PlatformJobService(mapper);
        when(mapper.selectById(5L)).thenReturn(job(5L, PlatformJob.PENDING, 0, 3, 8L));

        BusinessException error = assertThrows(BusinessException.class, () -> service.requireOwned(5L, 9L));

        assertEquals(403, error.getCode());
    }

    private PlatformJob job(Long id, String status, int attempts, int maxAttempts, Long createdBy) {
        PlatformJob job = new PlatformJob();
        job.setId(id);
        job.setStatus(status);
        job.setAttempts(attempts);
        job.setMaxAttempts(maxAttempts);
        job.setCreatedBy(createdBy);
        return job;
    }
}
