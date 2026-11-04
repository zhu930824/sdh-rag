package cn.sdh.backend.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.List;

/** Deletes one physical generation from all retrieval tiers; repeatable after partial success. */
@Service @RequiredArgsConstructor
public class IndexGenerationDeletion {
    private final ElasticsearchClient elastic;
    private final ElasticsearchIndexService indices;
    public void delete(IndexGenerationLifecycle.CleanupClaim claim) throws IOException {
        var generation=claim.generation();
        Query chunks=PublishedIndexService.generationFilter(List.of(generation),"document_id");
        Query summaries=generation.legacy()
                ? Query.of(q -> q.bool(b -> b.mustNot(n -> n.exists(e -> e.field("index_generation")))
                    .filter(f -> f.term(t -> t.field("doc_id").value(generation.documentId())))))
                : PublishedIndexService.generationFilter(List.of(generation),"doc_id");
        String hot=indices.getIndexName(claim.knowledgeId());
        remove(hot,chunks); remove(hot+"-cold",chunks);
        remove(SummaryIndexService.SUMMARY_INDEX_PREFIX+claim.knowledgeId(),summaries);
        remove("sdh-rag-summary-cold-"+claim.knowledgeId(),summaries);
    }
    private void remove(String index,Query filter) throws IOException {
        if(!elastic.indices().exists(e -> e.index(index)).value()) return;
        var result=elastic.deleteByQuery(d -> d.index(index).query(filter).refresh(true).conflicts(Conflicts.Proceed));
        if(result.timedOut() || result.versionConflicts()>0 || !result.failures().isEmpty()) {
            throw new IllegalStateException("索引批次删除未完整确认: " + index);
        }
    }
}
