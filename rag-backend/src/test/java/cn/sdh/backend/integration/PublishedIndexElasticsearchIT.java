package cn.sdh.backend.integration;

import cn.sdh.backend.dto.DocumentLinkConfig;
import cn.sdh.backend.job.*;
import cn.sdh.backend.service.*;
import cn.sdh.backend.service.factory.ChatModelFactory;
import cn.sdh.backend.service.impl.*;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.ai.document.Document;
import java.io.StringReader;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PublishedIndexElasticsearchIT {
    @Test void nativeSearchesExcludeStagingAndKeepThePinnedGenerationAfterPublication() throws Exception {
        try (var node = new ManagedElasticsearch();
             var transport = new RestClientTransport(RestClient.builder(HttpHost.create(node.url())).build(), new JacksonJsonpMapper())) {
            var es = new ElasticsearchClient(transport);
            var source = new SingleConnectionDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "", true);
            try {
                var jdbc = new JdbcTemplate(source);
                jdbc.execute("CREATE TABLE knowledge_document_relation(id BIGINT PRIMARY KEY,knowledge_id BIGINT,document_id BIGINT,index_revision INT,job_id BIGINT,process_status INT,chunk_count INT,error_message VARCHAR(2000),process_time TIMESTAMP,create_time TIMESTAMP)");
                jdbc.update("INSERT INTO knowledge_document_relation VALUES(1,7,42,1,11,2,1,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                new ResourceDatabasePopulator(new ClassPathResource("db/migration/V38__published_index_generations.sql"),new ClassPathResource("db/migration/V39__index_read_leases_and_cleanup.sql")).execute(source);
                var policy=new cn.sdh.backend.config.IndexLifecycleProperties(); policy.setRetentionSeconds(0);
                var lifecycle=new IndexGenerationLifecycle(jdbc,new DataSourceTransactionManager(source),policy);
                var publications = new PublishedIndexService(jdbc, new DataSourceTransactionManager(source),lifecycle);
                var indices = mock(ElasticsearchIndexService.class);
                when(indices.indexExists(7L)).thenReturn(true); when(indices.getIndexName(7L)).thenReturn("sdh-rag-kb-7");
                var embeddings = mock(EmbeddingService.class);
                when(embeddings.getEmbeddingByModel(anyString(), eq("model"))).thenReturn(new float[]{1,0});
                var vectors = new VectorStoreServiceImpl(es, indices, embeddings, null, publications);
                var chunkIndices=List.of("sdh-rag-kb-7","sdh-rag-kb-7-cold");
                var deletion=new IndexGenerationDeletion(es,indices);
                for (String index : chunkIndices) {
                    es.indices().create(c -> c.index(index).withJson(new StringReader("""
                        {"mappings":{"properties":{"document_id":{"type":"keyword"},"index_generation":{"type":"keyword"},"index_revision":{"type":"integer"},"content":{"type":"text","analyzer":"standard"},"embedding":{"type":"dense_vector","dims":2,"index":true,"similarity":"cosine"}}}}
                        """)));
                    es.index(i -> i.index(index).id("old").document(chunk(42, 1, null)));
                    es.index(i -> i.index(index).id("foreign").document(chunk(99, 2, "foreign-generation")));
                }
                var ctx = mock(PlatformJobExecutionContext.class); when(ctx.jobId()).thenReturn(12L);
                var payload = new DocumentIndexJobPayload(1L,42L,7L,2,new DocumentLinkConfig());
                jdbc.update("UPDATE knowledge_document_relation SET index_revision=2,job_id=12,process_status=1 WHERE id=1");
                var stage = publications.stage(payload,12L,"a".repeat(64),ctx);
                for (String index : chunkIndices) {
                    es.index(i -> i.index(index).id("new").document(chunk(42,2,stage.key())));
                    es.indices().refresh(r -> r.index(index));
                }
                String oldVersion;
                try (var old = publications.open(7L)) {
                    oldVersion = old.snapshot().version();
                    assertSearches(vectors,"old"); // Includes actual legacy Painless execution inside native kNN.
                    assertTrue(publications.publish(payload,12L,stage.key(),1,ctx));
                    assertSearches(vectors,"old");
                    assertEquals(oldVersion,publications.current(7L).snapshot().version());
                    assertTrue(lifecycle.claim(7L).isEmpty(),"The pinned reader must protect legacy vectors from cleanup");
                    assertTrue(es.exists(e -> e.index("sdh-rag-kb-7").id("old")).value());
                }
                var retired=lifecycle.claim(7L);
                assertEquals(1,retired.size());
                deletion.delete(retired.getFirst()); assertTrue(lifecycle.finish(retired.getFirst(),true));
                assertFalse(es.exists(e -> e.index("sdh-rag-kb-7").id("old")).value());
                assertFalse(es.exists(e -> e.index("sdh-rag-kb-7-cold").id("old")).value());
                try (var current = publications.open(7L)) {
                    assertNotEquals(oldVersion,current.snapshot().version());
                    assertSearches(vectors,"new");
                    assertFalse(current.snapshot().summariesComplete());
                }
                es.indices().create(c -> c.index(SummaryIndexService.SUMMARY_INDEX_PREFIX+7).withJson(new StringReader("""
                    {"mappings":{"properties":{"doc_id":{"type":"long"},"index_generation":{"type":"keyword"},"summary_embedding":{"type":"dense_vector","dims":2,"index":true,"similarity":"cosine"}}}}
                    """)));
                var summaries = new SummaryIndexServiceImpl(es,embeddings,mock(ChatModelFactory.class),publications);
                summaries.indexDocumentSummary(42L,7L,"title","question document content","model",Map.of("index_generation",stage.key()));
                es.index(i -> i.index(SummaryIndexService.SUMMARY_INDEX_PREFIX+7).id("unpublished").document(Map.of("doc_id",99,"index_generation","foreign-generation","summary_embedding",List.of(1f,0f))));
                es.indices().refresh(r -> r.index(SummaryIndexService.SUMMARY_INDEX_PREFIX+7));
                try (var current = publications.open(7L)) {
                    assertTrue(current.snapshot().summariesComplete());
                    assertEquals(Set.of(42L),summaries.coarseRetrieval("question",7L,10,"model",Map.of()).keySet());
                }
                es.indices().create(c -> c.index("sdh-rag-summary-cold-7").mappings(m -> m.properties("index_generation",p -> p.keyword(k -> k))));
                var summaryIndices=List.of("sdh-rag-summary-7","sdh-rag-summary-cold-7");
                var allIndices=List.of("sdh-rag-kb-7","sdh-rag-kb-7-cold","sdh-rag-summary-7","sdh-rag-summary-cold-7");
                jdbc.update("INSERT INTO knowledge_index_generation(generation_key,relation_id,knowledge_id,document_id,index_revision,state) VALUES('retired-stage',1,7,42,2,'STAGING')");
                for(String index:chunkIndices) es.index(i -> i.index(index).id("late").document(chunk(42,2,"retired-stage")));
                for(String index:summaryIndices) es.index(i -> i.index(index).id("late").document(Map.of("doc_id",42,"index_generation","retired-stage","summary_embedding",List.of(1f,0f))));
                es.indices().refresh(r -> r.index(allIndices));
                var orphan=lifecycle.claim(7L).getFirst();
                assertEquals("retired-stage",orphan.generation().key());
                deletion.delete(orphan); assertTrue(lifecycle.finish(orphan,true));
                for(String index:allIndices) assertFalse(es.exists(e -> e.index(index).id("late")).value());
                assertTrue(es.exists(e -> e.index("sdh-rag-kb-7").id("new")).value());
                assertTrue(es.exists(e -> e.index("sdh-rag-summary-7").id(stage.key())).value());
                // An expired writer can wake after the first deletion: the durable tombstone must resweep it.
                es.index(i -> i.index("sdh-rag-kb-7").id("late").document(chunk(42,2,"retired-stage")));
                es.indices().refresh(r -> r.index("sdh-rag-kb-7"));
                jdbc.update("UPDATE knowledge_index_generation SET next_cleanup_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE generation_key='retired-stage'");
                assertEquals(List.of(7L),lifecycle.dueKnowledgeBases());
                var resweep=lifecycle.claim(7L).getFirst();
                deletion.delete(resweep); assertTrue(lifecycle.finish(resweep,true));
                assertFalse(es.exists(e -> e.index("sdh-rag-kb-7").id("late")).value());
            } finally { source.destroy(); }
        }
    }
    private static Map<String,Object> chunk(long doc, int revision, String generation) {
        var result = new HashMap<String,Object>(); result.put("document_id",doc); result.put("index_revision",revision);
        result.put("content","question document content"); result.put("embedding",List.of(1f,0f)); result.put("chunk_index",0);
        if(generation!=null) result.put("index_generation",generation); return result;
    }
    private static void assertSearches(VectorStoreService vectors,String id) {
        assertIds(vectors.similaritySearch("question",7L,10,"model"),id);
        assertIds(vectors.keywordSearch("question",7L,10),id);
        assertIds(vectors.hybridSearch("question",7L,10,10,.7,.3,"model",null,Map.of()),id);
        assertIds(vectors.searchByDocumentIds(List.of("question"),Set.of(42L,99L),7L,10,"model",Map.of()),id);
        assertIds(vectors.coldSearch("question",7L,10,"model",Map.of()),id);
    }
    private static void assertIds(List<Document> documents,String id) {
        assertEquals(List.of(id),documents.stream().map(d -> d.getMetadata().get("id")).toList());
    }
}
