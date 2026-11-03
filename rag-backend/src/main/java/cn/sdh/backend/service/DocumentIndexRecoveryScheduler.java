package cn.sdh.backend.service;

import cn.sdh.backend.entity.KnowledgeDocumentRelation;
import cn.sdh.backend.mapper.KnowledgeDocumentRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIndexRecoveryScheduler {
    private final KnowledgeDocumentRelationMapper relations;
    private final DocumentProcessService documentProcessService;

    @Scheduled(fixedDelayString = "${knowledge.documents.recovery-delay-ms:60000}",
            initialDelayString = "${knowledge.documents.recovery-initial-delay-ms:30000}")
    public void recoverPendingRelations() {
        for (KnowledgeDocumentRelation relation : relations.selectPendingWithoutJob(50)) {
            try {
                documentProcessService.recoverPendingRelation(relation.getId());
            } catch (Exception e) {
                log.warn("恢复待处理文档索引失败: relationId={}, error={}", relation.getId(), e.getMessage());
            }
        }
    }
}
