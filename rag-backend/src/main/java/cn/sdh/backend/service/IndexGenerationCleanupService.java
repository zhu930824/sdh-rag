package cn.sdh.backend.service;

import cn.sdh.backend.config.IndexLifecycleProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j @Service @RequiredArgsConstructor
public class IndexGenerationCleanupService {
    private final IndexLifecycleProperties policy;
    private final IndexGenerationLifecycle lifecycle;
    private final IndexGenerationDeletion deletion;
    public void scan() {
        lifecycle.reapExpiredLeases();
        if(!policy.isCleanupEnabled()) return;
        for(Long knowledge:lifecycle.dueKnowledgeBases()) {
            try {
                for(var claim:lifecycle.claim(knowledge)) {
                    boolean success=false;
                    try { deletion.delete(claim); success=true; }
                    catch(Exception failure) { log.warn("索引批次回收未完成，将重试: generation={}",claim.generation().key()); }
                    lifecycle.finish(claim,success);
                }
            } catch(Exception unavailable) { log.warn("索引回收协调失败，保留批次等待下次扫描: knowledgeId={}",knowledge); }
        }
    }
}
