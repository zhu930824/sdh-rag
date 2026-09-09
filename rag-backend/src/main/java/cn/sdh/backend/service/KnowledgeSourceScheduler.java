package cn.sdh.backend.service;

import cn.sdh.backend.entity.KnowledgeSource;
import cn.sdh.backend.job.KnowledgeSourcePullJobHandler;
import cn.sdh.backend.mapper.KnowledgeSourceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class KnowledgeSourceScheduler {
    private final KnowledgeSourceMapper sources;
    private final PlatformJobService jobs;
    private final ObjectMapper objectMapper;

    public KnowledgeSourceScheduler(KnowledgeSourceMapper sources,
                                    PlatformJobService jobs,
                                    ObjectMapper objectMapper) {
        this.sources = sources;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
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
            enqueue(source);
        }
    }

    private void enqueue(KnowledgeSource source) {
        try {
            String scheduleKey = source.getNextSyncTime() == null ? "due" : source.getNextSyncTime().toString();
            jobs.enqueue(new PlatformJobService.EnqueueCommand(
                    "knowledge-pull:" + source.getId() + ":" + scheduleKey,
                    KnowledgeSourcePullJobHandler.TYPE, "KNOWLEDGE_SOURCE", source.getId(),
                    objectMapper.writeValueAsString(new KnowledgeSourcePullJobHandler.Payload(source.getId())),
                    10, 1, source.getCreatedBy()));
        } catch (Exception e) {
            log.warn("知识源同步任务入队失败: sourceId={}, error={}", source.getId(), e.getMessage());
        }
    }
}
