package cn.sdh.backend.service.impl;

import cn.sdh.backend.service.*;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PublishedIndexQueryTest {
    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    void publishedGenerationConstraintIsInsideKnnAndKeywordQueries() throws Exception {
        var source = new SingleConnectionDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "", true);
        try {
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE knowledge_document_relation(id BIGINT PRIMARY KEY,knowledge_id BIGINT,document_id BIGINT,index_revision INT,job_id BIGINT,process_status INT,chunk_count INT,error_message VARCHAR(2000),process_time TIMESTAMP,create_time TIMESTAMP)");
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V38__published_index_generations.sql")).execute(source);
            jdbc.update("INSERT INTO knowledge_document_relation(id,knowledge_id,document_id,index_revision,job_id,process_status,published_generation) VALUES(1,7,42,2,12,1,'g:1:1')");
            jdbc.update("INSERT INTO knowledge_index_generation(generation_key,relation_id,knowledge_id,document_id,index_revision,state) VALUES('g:1:1',1,7,42,1,'PUBLISHED')");
            var publications = new PublishedIndexService(jdbc, new DataSourceTransactionManager(source));
            var elastic = mock(ElasticsearchClient.class); var indices = mock(ElasticsearchIndexService.class); var embeddings = mock(EmbeddingService.class);
            when(indices.indexExists(7L)).thenReturn(true); when(indices.getIndexName(7L)).thenReturn("index-7");
            when(embeddings.getEmbeddingByModel("question", "model")).thenReturn(new float[]{1,0});
            AtomicReference<SearchRequest> request = new AtomicReference<>();
            when(elastic.search(any(Function.class), eq(Map.class))).thenAnswer(call -> {
                Function builder = call.getArgument(0);
                request.set((SearchRequest)((co.elastic.clients.util.ObjectBuilder)builder.apply(new SearchRequest.Builder())).build());
                return new SearchResponse.Builder<Map>().took(0).timedOut(false).shards(s -> s.total(1).successful(1).failed(0)).hits(h -> h.hits(List.of())).build();
            });
            var vectors = new VectorStoreServiceImpl(elastic, indices, embeddings, null, publications);
            try (var view = publications.open(7L)) {
                vectors.similaritySearch("question", 7L, 3, "model");
                assertEquals(1, request.get().knn().size());
                assertFalse(request.get().knn().getFirst().filter().isEmpty(), "The kNN pre-filter must exclude the staging generation");
                assertTrue(request.get().knn().getFirst().filter().toString().contains("g:1:1"));
                vectors.keywordSearch("question", 7L, 3);
                assertTrue(request.get().query().toString().contains("g:1:1"));
            }
            var proxyFactory = new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(vectors);
            proxyFactory.addAspect(new PublishedIndexReadAspect(publications));
            VectorStoreService proxied = proxyFactory.getProxy();
            proxied.hybridSearch("question", 7L, 3, 3, .7, .3, "model", null, Map.of());
            assertTrue(request.get().query().toString().contains("g:1:1"));
            assertThrows(IllegalStateException.class, () -> publications.current(7L), "The read scope must not leak to the next request");
            proxied.coldSearch("question", 7L, 3, "model", Map.of());
            assertTrue(request.get().knn().getFirst().filter().toString().contains("g:1:1"));
            proxied.searchByDocumentIds(List.of("question"), Set.of(42L), 7L, 3, "model", Map.of());
            assertTrue(request.get().knn().getFirst().filter().toString().contains("g:1:1"));
        } finally { source.destroy(); }
    }
}
