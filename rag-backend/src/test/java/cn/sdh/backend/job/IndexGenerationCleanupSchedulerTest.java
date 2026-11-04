package cn.sdh.backend.job;

import cn.sdh.backend.service.IndexGenerationCleanupService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class IndexGenerationCleanupSchedulerTest {
    @Test void slowSweepsDoNotOverlapAndARejectedHandoffCanRecover() {
        var cleanup=mock(IndexGenerationCleanupService.class); var executor=mock(Executor.class);
        doThrow(new RejectedExecutionException()).doNothing().when(executor).execute(any());
        var scheduler=new IndexGenerationCleanupScheduler(cleanup,executor);
        scheduler.dispatch(); scheduler.dispatch(); scheduler.dispatch();
        var tasks=ArgumentCaptor.forClass(Runnable.class);
        verify(executor,times(2)).execute(tasks.capture()); verifyNoInteractions(cleanup);
        tasks.getAllValues().getLast().run(); verify(cleanup).scan();
        scheduler.dispatch(); verify(executor,times(3)).execute(any());
    }
}
