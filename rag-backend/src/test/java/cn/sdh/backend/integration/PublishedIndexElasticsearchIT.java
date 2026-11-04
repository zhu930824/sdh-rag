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
                new ResourceDatabasePopulator(new ClassPathResource("db/migration/V38__published_index_generations.sql")).execute(source);
                var publications = new PublishedIndexService(jdbc, new DataSourceTransactionManager(source));
                var indices = mock(ElasticsearchIndexService.class);
                when(indices.indexExists(7L)).thenReturn(true); when(indices.getIndexName(7L)).thenReturn("it-hot");
                var embeddings = mock(EmbeddingService.class);
                when(embeddings.getEmbeddingByModel(anyString(), eq("model"))).thenReturn(new float[]{1,0});
                var vectors = new VectorStoreServiceImpl(es, indices, embeddings, null, publications);
                for (String index : List.of("it-hot", "sdh-rag-kb-7-cold")) {
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
                for (String index : List.of("it-hot", "sdh-rag-kb-7-cold")) {
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
                }
                try (var current = publications.open(7L)) {
                    assertNotEquals(oldVersion,current.snapshot().version());
                    assertSearches(vectors,"new");
                    assertFalse(current.snapshot().summariesComplete());
                }
                assertTrue(es.exists(e -> e.index("it-hot").id("old")).value(),"Published old vectors must remain available to pinned readers");
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
