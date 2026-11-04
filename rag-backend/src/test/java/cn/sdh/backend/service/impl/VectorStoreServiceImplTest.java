package cn.sdh.backend.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import cn.sdh.backend.service.*;
import cn.sdh.backend.entity.KnowledgeChunk;

class VectorStoreServiceImplTest {
    @Test
    void vectorIdsAreStablePerKnowledgeDocumentAndChunk() {
        String first = VectorStoreServiceImpl.deterministicVectorId(1L, 2L, 3, 4);

        assertEquals(first, VectorStoreServiceImpl.deterministicVectorId(1L, 2L, 3, 4));
        assertNotEquals(first, VectorStoreServiceImpl.deterministicVectorId(1L, 2L, 4, 4));
        assertNotEquals(first, VectorStoreServiceImpl.deterministicVectorId(2L, 2L, 3, 4));
        assertNotEquals(first, VectorStoreServiceImpl.deterministicVectorId(1L, 2L, 3, 5));
    }
    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    void storedAndRetrievedVectorsKeepGenerationAndContentFingerprints() throws Exception {
        var elastic = mock(ElasticsearchClient.class); var indices = mock(ElasticsearchIndexService.class);
        var embeddings = mock(EmbeddingService.class);
        // The two mocked boundaries do not share index state; the fixture starts with an existing index.
        when(indices.indexExists(7L)).thenReturn(true);
        when(indices.getIndexName(7L)).thenReturn("index-7"); when(embeddings.getEmbeddingByModel("indexed text", "model")).thenReturn(new float[]{1, 0});
        var service = new VectorStoreServiceImpl(elastic, indices, embeddings, null);
        AtomicReference<IndexRequest> saved = new AtomicReference<>();
        doAnswer(call -> {
            saved.set(call.getArgument(0));
            return null;
        }).when(elastic).index(any(IndexRequest.class));
        KnowledgeChunk chunk = new KnowledgeChunk(); chunk.setDocumentId(42L); chunk.setChunkIndex(0); chunk.setContent("indexed text");
        String id = service.addVector(chunk, 7L, "model", Map.of("index_revision", 3, "indexed_content_sha256", "a".repeat(64), "chunk_content_sha256", "forged"));
        assertNotNull(id);
        Map<String, Object> source = (Map<String, Object>) saved.get().document();
        assertEquals("79db5b6ba12bffb3af6679dcacdb7b4685d3336ccb1aee28c3220694d61eb6ad", source.get("chunk_content_sha256"));
        var response = new GetResponse.Builder<Map>().index("index-7").id(id).found(true).source(source).build();
        when(elastic.get(any(Function.class), eq(Map.class))).thenReturn(response);
        var retrieved = service.getChunkById(id, 7L);
        assertEquals(3, retrieved.getMetadata().get("index_revision"));
        assertEquals("a".repeat(64), retrieved.getMetadata().get("indexed_content_sha256"));
        var provenance = cn.sdh.backend.dto.RetrievedChunkProvenance.from(retrieved);
        assertEquals(id, provenance.vectorId()); assertTrue(provenance.indexedChunkHashVerified());
    }
}
