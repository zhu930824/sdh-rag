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
            String database="jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL";
            var source = new SingleConnectionDataSource(database,"sa","",true);
            source.getConnection();
            var connections=new DriverManagerDataSource(database,"sa","");
            try {
                var jdbc = new JdbcTemplate(connections);
                jdbc.execute("CREATE TABLE knowledge_document_relation(id BIGINT PRIMARY KEY,knowledge_id BIGINT,document_id BIGINT,index_revision INT,job_id BIGINT,process_status INT,chunk_count INT,error_message VARCHAR(2000),process_time TIMESTAMP,create_time TIMESTAMP)");
                jdbc.update("INSERT INTO knowledge_document_relation VALUES(1,7,42,1,11,2,1,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                new ResourceDatabasePopulator(new ClassPathResource("db/migration/V24__knowledge_publishing_pipeline.sql"),new ClassPathResource("db/migration/V38__published_index_generations.sql"),new ClassPathResource("db/migration/V39__index_read_leases_and_cleanup.sql"),new ClassPathResource("db/migration/V40__index_publication_quality_gate.sql")).execute(connections);
                var policy=new cn.sdh.backend.config.IndexLifecycleProperties(); policy.setRetentionSeconds(0);
                var lifecycle=new IndexGenerationLifecycle(jdbc,new DataSourceTransactionManager(connections),policy);
                var publications = new PublishedIndexService(jdbc, new DataSourceTransactionManager(connections),lifecycle,new IndexPublicationGateStore(jdbc,org.mockito.Mockito.mock(AgentImprovementService.class)));
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
                // A business draft is searchable only in the worker's candidate scope until its gate commits.
                jdbc.execute("CREATE TABLE knowledge_document(id BIGINT PRIMARY KEY,status INT)");
                jdbc.update("INSERT INTO knowledge_document VALUES(42,0),(43,0)");
                String approved="approved content ".repeat(10);
                jdbc.update("INSERT INTO knowledge_draft(id,knowledge_id,title,content,status,regression_queries,published_document_id,content_checksum,created_by,reviewed_by,reviewed_at,created_at,updated_at) VALUES(31,7,'approved',?,'PUBLISHING','[\"question\"]',42,'checksum',7,7,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",approved);
                jdbc.update("UPDATE knowledge_draft SET published_generation=? WHERE id=31",stage.key());
                jdbc.update("UPDATE knowledge_document_relation SET index_revision=3,job_id=13,index_source_sha256=NULL,index_write_generation=NULL,process_status=1 WHERE id=1");
                var managedContext=mock(PlatformJobExecutionContext.class); when(managedContext.jobId()).thenReturn(13L);
                var managedConfig=new DocumentLinkConfig(); managedConfig.setEmbeddingModel("model");
                var managedPayload=new DocumentIndexJobPayload(1L,42L,7L,3,managedConfig);
                String approvedSha=cn.sdh.backend.utils.ContentFingerprint.sha256(KnowledgePublicationContent.markdown("approved",approved));
                var managed=publications.stage(managedPayload,13L,"c".repeat(64),approvedSha,managedContext);
                es.index(i -> i.index("sdh-rag-kb-7").id("managed").document(chunk(42,3,managed.key())));
                es.indices().refresh(r -> r.index("sdh-rag-kb-7"));
                var proxyFactory=new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(vectors);
                proxyFactory.addAspect(new PublishedIndexReadAspect(publications));
                VectorStoreService online=proxyFactory.getProxy();
                try(var candidate=publications.openCandidate(managedPayload,13L,managed.key(),managedContext)) {
                    assertIds(vectors.candidateSearch("question",7L,5,"model"),"managed");
                    var live=java.util.concurrent.CompletableFuture.supplyAsync(() -> online.similaritySearch("question",7L,5,"model")).get(10,java.util.concurrent.TimeUnit.SECONDS);
                    assertIds(live,"new");
                }
                var gate=new IndexPublicationGate(publications,new IndexPublicationGateStore(jdbc,mock(AgentImprovementService.class)),online);
                assertTrue(gate.evaluate(managedPayload,13L,managed.key(),1,"model",managedContext));
                assertIds(online.similaritySearch("question",7L,5,"model"),"new");
                assertTrue(publications.publish(managedPayload,13L,managed.key(),1,managedContext));
                assertIds(online.similaritySearch("question",7L,5,"model"),"managed");
                assertEquals(1,online.countPublishedChunks(7L));
                assertIds(online.getPublishedChunks(7L,0,10),"managed");
                // Five stronger published competitors keep an inferior candidate outside Top 5.
                for(long id=100;id<105;id++) {
                    String key="competitor-"+id;
                    jdbc.update("INSERT INTO knowledge_document_relation(id,knowledge_id,document_id,index_revision,published_generation) VALUES(?,7,?,1,?)",id,id,key);
                    jdbc.update("INSERT INTO knowledge_index_generation(generation_key,relation_id,knowledge_id,document_id,index_revision,state) VALUES(?,?,7,?,1,'PUBLISHED')",key,id,id);
                    long document=id;
                    es.index(i -> i.index("sdh-rag-kb-7").id(key).document(chunk(document,1,key)));
                }
                jdbc.update("INSERT INTO knowledge_draft(id,knowledge_id,title,content,status,regression_queries,published_document_id,content_checksum,created_by,reviewed_by,reviewed_at,created_at,updated_at) VALUES(32,7,'approved',?,'PUBLISHING','[\"question\"]',43,'checksum',7,7,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",approved);
                jdbc.update("INSERT INTO knowledge_document_relation(id,knowledge_id,document_id,index_revision,job_id,process_status) VALUES(3,7,43,1,14,1)");
                var rejectedContext=mock(PlatformJobExecutionContext.class); when(rejectedContext.jobId()).thenReturn(14L);
                var rejectedConfig=new DocumentLinkConfig(); rejectedConfig.setEmbeddingModel("model");
                var rejectedPayload=new DocumentIndexJobPayload(3L,43L,7L,1,rejectedConfig);
                var rejected=publications.stage(rejectedPayload,14L,"d".repeat(64),approvedSha,rejectedContext);
                var inferior=chunk(43,1,rejected.key()); inferior.put("embedding",List.of(-1f,0f));
                es.index(i -> i.index("sdh-rag-kb-7").id("rejected").document(inferior));
                es.indices().refresh(r -> r.index("sdh-rag-kb-7"));
                assertFalse(gate.evaluate(rejectedPayload,14L,rejected.key(),1,"model",rejectedContext));
                assertNull(jdbc.queryForObject("SELECT published_generation FROM knowledge_document_relation WHERE id=3",String.class));
                assertEquals("GATE_FAILED",jdbc.queryForObject("SELECT status FROM knowledge_draft WHERE id=32",String.class));
                assertTrue(online.similaritySearch("question",7L,10,"model").stream().noneMatch(d -> "rejected".equals(d.getMetadata().get("id"))));
            } finally { source.destroy(); }
        }
    }
    private static Map<String,Object> chunk(long doc, int revision, String generation) {
        var result = new HashMap<String,Object>(); result.put("document_id",doc); result.put("index_revision",revision);
        result.put("content","question document content"); result.put("embedding",List.of(1f,0f)); result.put("chunk_index",0);
        if(generation!=null) result.put("index_generation",generation); return result;
    }
    private static void assertSearches(VectorStoreService vectors,String id) {
        assertEquals(1,vectors.countPublishedChunks(7L));
        assertIds(vectors.getPublishedChunks(7L,0,10),id);
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
