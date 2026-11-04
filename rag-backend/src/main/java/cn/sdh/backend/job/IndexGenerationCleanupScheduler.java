package cn.sdh.backend.job;

import cn.sdh.backend.service.IndexGenerationCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Non-blocking scheduled handoff; at most one bounded sweep per application instance. */
@Slf4j @Component
public class IndexGenerationCleanupScheduler {
    private final IndexGenerationCleanupService cleanup;
    private final Executor executor;
    private final AtomicBoolean running=new AtomicBoolean();
    public IndexGenerationCleanupScheduler(IndexGenerationCleanupService cleanup,@Qualifier("indexCleanupExecutor") Executor executor) {
        this.cleanup=cleanup; this.executor=executor;
    }
    @Scheduled(fixedDelayString="${knowledge.index-lifecycle.cleanup-interval-ms:60000}")
    public void dispatch() {
        if(!running.compareAndSet(false,true)) return;
        try {
            executor.execute(() -> { try { cleanup.scan(); } finally { running.set(false); } });
        } catch(RuntimeException rejected) {
            running.set(false);
            log.warn("索引回收执行器暂不可用，下次扫描重试");
        }
    }
}
