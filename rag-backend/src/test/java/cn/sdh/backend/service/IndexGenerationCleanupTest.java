package cn.sdh.backend.service;

import cn.sdh.backend.config.IndexLifecycleProperties;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class IndexGenerationCleanupTest {
    @Test void cleanupRequiresTheExplicitRolloutSwitchAndRetriesPartialFailure() throws Exception {
        var policy=new IndexLifecycleProperties(); var lifecycle=mock(IndexGenerationLifecycle.class);
        var deletion=mock(IndexGenerationDeletion.class);
        var service=new IndexGenerationCleanupService(policy,lifecycle,deletion);
        service.scan(); verify(lifecycle).reapExpiredLeases(); verifyNoMoreInteractions(lifecycle); verifyNoInteractions(deletion);
        policy.setCleanupEnabled(true);
        when(lifecycle.dueKnowledgeBases()).thenReturn(List.of(7L));
        var first=claim("a"); var second=claim("b");
        when(lifecycle.claim(7L)).thenReturn(List.of(first,second));
        doThrow(new IOException("partial deletion")).when(deletion).delete(first);
        service.scan();
        verify(lifecycle).finish(first,false); verify(lifecycle).finish(second,true);
    }
    @Test @SuppressWarnings("unchecked")
    void elasticVersionConflictsAreNeverRecordedAsSuccessfulCleanup() throws Exception {
        var es=mock(ElasticsearchClient.class); var indices=mock(ElasticsearchIndicesClient.class);
        var names=mock(ElasticsearchIndexService.class);
        when(es.indices()).thenReturn(indices); when(names.getIndexName(7L)).thenReturn("index-7");
        when(indices.exists(any(Function.class))).thenReturn(new BooleanResponse(true));
        var result=new DeleteByQueryResponse.Builder().withJson(new StringReader("""
            {"took":1,"timed_out":false,"total":1,"deleted":0,"batches":1,"version_conflicts":1,"noops":0,"retries":{"bulk":0,"search":0},"throttled_millis":0,"requests_per_second":-1,"throttled_until_millis":0,"failures":[]}
            """)).build();
        when(es.deleteByQuery(any(Function.class))).thenReturn(result);
        assertThrows(IllegalStateException.class,() -> new IndexGenerationDeletion(es,names).delete(claim("a")));
    }
    private static IndexGenerationLifecycle.CleanupClaim claim(String key) {
        return new IndexGenerationLifecycle.CleanupClaim(7L,new PublishedIndexService.Generation(key,42L,1,false,null),"owner");
    }
}
