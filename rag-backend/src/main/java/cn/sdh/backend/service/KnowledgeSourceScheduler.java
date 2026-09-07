package cn.sdh.backend.service;

import cn.sdh.backend.entity.KnowledgeSource;
import cn.sdh.backend.mapper.KnowledgeSourceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class KnowledgeSourceScheduler {
    private final KnowledgeSourceMapper sources;
    private final KnowledgeSourceSyncCoordinator coordinator;
    private final Executor executor;
    private final Set<Long> localInFlight = ConcurrentHashMap.newKeySet();

    public KnowledgeSourceScheduler(KnowledgeSourceMapper sources,
                                    KnowledgeSourceSyncCoordinator coordinator,
                                    @Qualifier("knowledgeSourceExecutor") Executor executor) {
        this.sources = sources;
        this.coordinator = coordinator;
        this.executor = executor;
    }

    @Value("${knowledge.sources.scheduler-batch-size:20}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${knowledge.sources.scheduler-delay-ms:60000}")
    public void dispatchDueSources() {
        int limit = Math.max(1, Math.min(batchSize, 100));
        List<KnowledgeSource> due = sources.selectList(new LambdaQueryWrapper<KnowledgeSource>()
                .eq(KnowledgeSource::getStatus, 1)
                .eq(KnowledgeSource::getSyncEnabled, 1)
                .le(KnowledgeSource::getNextSyncTime, LocalDateTime.now())
                .orderByAsc(KnowledgeSource::getNextSyncTime)
                .last("LIMIT " + limit));
        for (KnowledgeSource source : due) {
            if (!localInFlight.add(source.getId())) continue;
            try { executor.execute(() -> pull(source.getId())); }
            catch (RuntimeException e) {
                localInFlight.remove(source.getId());
                log.warn("提交知识源同步任务失败: sourceId={}, error={}", source.getId(), e.getMessage());
            }
        }
    }

    private void pull(Long sourceId) {
        try { coordinator.pullScheduled(sourceId); }
        catch (Exception e) { log.warn("定时拉取知识源失败: sourceId={}, error={}", sourceId, e.getMessage()); }
        finally { localInFlight.remove(sourceId); }
    }
}
