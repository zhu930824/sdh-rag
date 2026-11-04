package cn.sdh.backend.service;

import cn.sdh.backend.job.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** Runs actual retrieval against published heads plus one isolated candidate generation. */
@Service @RequiredArgsConstructor
public class IndexPublicationGate {
    private final PublishedIndexService publications;
    private final IndexPublicationGateStore store;
    private final VectorStoreService vectors;
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${knowledge.publishing.recovery-delay-ms:60000}")
    public void reconcileTerminatedJobs() { store.reconcileTerminatedJobs(); }
    public boolean evaluate(DocumentIndexJobPayload payload,Long jobId,String generation,int chunks,String embeddingModel,PlatformJobExecutionContext context) {
        var policy=store.frozen(generation);
        if(policy==null) return true;
        if(chunks>0 && !Objects.equals(policy.embeddingModel(),embeddingModel)) throw new cn.sdh.backend.common.exception.BusinessException(409,"门禁嵌入模型选择已变化，请重新索引");
        try(var view=publications.openCandidate(payload,jobId,generation,context)) {
            List<KnowledgePublishingService.ValidationItem> items=new ArrayList<>();
            for(String query:policy.queries()) {
                context.checkpoint();
                var hits=chunks==0?List.<org.springframework.ai.document.Document>of():vectors.candidateSearch(query,payload.knowledgeId(),policy.topK(),embeddingModel);
                Integer rank=null;
                for(int i=0;i<hits.size();i++) {
                    var metadata=hits.get(i).getMetadata();
                    if(generation.equals(metadata.get("index_generation")) && payload.documentId().toString().equals(Objects.toString(metadata.get("document_id"),null))) { rank=i+1; break; }
                }
                items.add(new KnowledgePublishingService.ValidationItem(query,rank!=null,rank));
            }
            var result=new KnowledgePublishingService.ValidationResult(items.stream().allMatch(KnowledgePublishingService.ValidationItem::passed),items);
            publications.saveGateEvaluation(payload,jobId,generation,view.basisVersion(),view.snapshot().version(),result,context);
            return result.passed();
        }
    }
}
