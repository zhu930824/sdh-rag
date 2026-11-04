package cn.sdh.backend.job;

import cn.sdh.backend.service.PlatformJobService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformJobDispatcherTest {
    @Test void everyClaimBatchHasAnIndependentLeaseOwner() {
        var jobs = mock(PlatformJobService.class);
        var executor = mock(ThreadPoolTaskExecutor.class);
        when(executor.getMaxPoolSize()).thenReturn(2);
        var dispatcher = new PlatformJobDispatcher(jobs, List.of(), executor);
        dispatcher.dispatch();
        dispatcher.dispatch();
        var owners = ArgumentCaptor.forClass(String.class);
        verify(jobs, times(2)).claimAvailable(owners.capture(), anyInt());
        assertNotEquals(owners.getAllValues().get(0), owners.getAllValues().get(1),
                "A late execution must not inherit the lease of a retry on the same dispatcher");
    }
}
