package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgeSourceSyncCoordinator {
    private final KnowledgeSourceService sourceService;
    private final KnowledgeSourceSyncLock syncLock;

    public KnowledgeSourceService.IngestResult pullNow(Long knowledgeId, Long sourceId) {
        KnowledgeSourceSyncLock.LockHandle handle = syncLock.acquire(sourceId);
        if (handle == null) throw new BusinessException(409, "该数据源正在同步，请稍后重试");
        try { return sourceService.pullNow(knowledgeId, sourceId); }
        finally { syncLock.release(handle); }
    }

    public KnowledgeSourceService.IngestResult pullScheduled(Long sourceId) {
        KnowledgeSourceSyncLock.LockHandle handle = syncLock.acquire(sourceId);
        if (handle == null) return null;
        try {
            return sourceService.pullScheduled(sourceId);
        } finally {
            syncLock.release(handle);
        }
    }
}
