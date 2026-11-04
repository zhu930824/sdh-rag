package cn.sdh.backend.service;

import cn.sdh.backend.dto.*;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RagEvidenceTest {
    @Test void emptyRetrievalStillPreservesItsPublishedKnowledgeVersion() {
        var publication = new IndexPublicationSnapshot("a".repeat(64),0,0,false);
        var snapshot = JSON.parseObject(RagEvidence.snapshot(List.of(),"",publication));
        assertEquals(publication.version(),snapshot.getString("publicationVersion"));
        assertEquals("NONE",snapshot.getString("generationCoverage"));
        assertTrue(snapshot.getJSONArray("chunks").isEmpty());
    }

    @Test void chatAndAgentRetrievalUseTheSameMultiChunkEvidence() {
        var bases = mock(KnowledgeBaseService.class); var search = mock(RagSearchService.class); var access = mock(KnowledgeAccessService.class);
        var base = new cn.sdh.backend.entity.KnowledgeBase(); base.setId(7L);
        when(bases.getKnowledgeBaseById(7L)).thenReturn(base); when(access.canRead(base, 1L)).thenReturn(true);
        when(search.searchWithCascade(anyString(), eq(base), anyList(), anyMap())).thenReturn(SearchResult.of(
                List.of(chunk(1L, 0, 2, "first"), chunk(1L, 1, 2, "second")), "question"));
        var chat = new cn.sdh.backend.service.impl.ChatServiceImpl(null, null, null, null, bases, search,
                null, null, null, null, null, null, new com.fasterxml.jackson.databind.ObjectMapper(), access, null, null);
        var retrieval = chat.retrieveForAgent("question", 7L, 1L, null, Map.of());
        assertFalse(retrieval.forbidden()); assertTrue(retrieval.context().contains("second"));
        assertEquals(2, JSON.parseArray(retrieval.sourcesPayloadJson()).getJSONObject(0).getJSONArray("chunks").size());
        assertEquals(2, JSON.parseObject(retrieval.retrievalSnapshotJson()).getJSONArray("chunks").size());
        when(access.canRead(base, 1L)).thenReturn(false);
        var denied = chat.retrieveForAgent("question", 7L, 1L, null, Map.of());
        assertTrue(denied.forbidden()); assertNull(denied.retrievalSnapshotJson());
    }
    @Test void allRetrievedChunksRemainInTheirDocumentCitation() {
        var sources = RagEvidence.sources(List.of(chunk(1L, 0, 2, "first"), chunk(1L, 1, 2, "second"), chunk(2L, 0, 1, "third")));
        assertEquals(2, sources.size());
        var group = JSON.parseObject(JSON.toJSONString(sources.getFirst()));
        assertEquals(2, group.getJSONArray("chunks").size());
        assertEquals("second", group.getJSONArray("chunks").getJSONObject(1).getString("content"));
        assertEquals(2, group.getJSONArray("chunks").getJSONObject(1).getIntValue("indexRevision"));
    }
    @Test void snapshotPreservesContentHashesAndDistinguishesLegacyAndMixedGenerations() {
        var a = chunk(1L, 0, 2, "first"); var b = chunk(1L, 1, 2, "second");
        String original = RagEvidence.snapshot(List.of(a, b), "context");
        assertEquals("COMPLETE", JSON.parseObject(original).getString("generationCoverage"));
        a.setContent("changed later");
        assertTrue(original.contains("first")); assertFalse(original.contains("changed later"));
        a.setContent("first");
        b.setIndexRevision(3);
        assertTrue(JSON.parseObject(RagEvidence.snapshot(List.of(a, b), "context")).getBooleanValue("mixedGenerations"));
        b.setIndexRevision(null);
        assertEquals("PARTIAL", JSON.parseObject(RagEvidence.snapshot(List.of(a, b), "context")).getString("generationCoverage"));
        a.setIndexRevision(null);
        assertEquals("NONE", JSON.parseObject(RagEvidence.snapshot(List.of(a, b), "context")).getString("generationCoverage"));
    }
    @Test void oversizedContentIsExplicitlyAPreviewWhileItsHashCoversTheFullChunk() {
        var chunk = chunk(1L, 0, 2, "中".repeat(20000));
        var snapshot = JSON.parseObject(RagEvidence.snapshot(List.of(chunk), "context"));
        var item = snapshot.getJSONArray("chunks").getJSONObject(0);
        assertTrue(item.getBooleanValue("truncated")); assertEquals(12000, item.getString("content").length());
        assertEquals(chunk.getChunkContentSha256(), item.getString("chunkContentSha256"));
    }
    @Test void invalidMetadataNeverBecomesAFabricatedKnownRevision() {
        for (Object revision : List.of(0, -1, 1.5, "bad", "2147483648")) {
            var provenance = RetrievedChunkProvenance.from(new Document("text", Map.of("index_revision", revision)));
            assertNull(provenance.indexRevision()); assertFalse(provenance.indexedChunkHashVerified());
        }
        var mismatched = RetrievedChunkProvenance.from(new Document("changed", Map.of("chunk_content_sha256", "a".repeat(64))));
        assertFalse(mismatched.indexedChunkHashVerified());
    }
    static SearchResult.RankedChunk chunk(Long id, int index, int revision, String text) {
        return SearchResult.RankedChunk.builder().documentId(id).documentTitle("document-" + id).content(text).chunkIndex(index)
                .vectorId(id + ":" + index).indexRevision(revision).indexedContentSha256("a".repeat(64))
                .chunkContentSha256(cn.sdh.backend.utils.ContentFingerprint.sha256(text)).indexedChunkHashVerified(true).relevanceScore(.9).build();
    }
}
