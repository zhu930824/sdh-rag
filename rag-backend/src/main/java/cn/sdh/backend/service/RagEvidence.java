package cn.sdh.backend.service;

import cn.sdh.backend.dto.SearchResult.RankedChunk;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import cn.sdh.backend.utils.ContentFingerprint;

/** Retrieval evidence formatting shared by streaming citations and durable snapshots. */
public final class RagEvidence {
    private RagEvidence() {}
    public static List<Map<String, Object>> sources(List<RankedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return null;
        Map<String, List<RankedChunk>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            grouped.computeIfAbsent(chunk.getDocumentId() == null ? "unknown-" + i : "doc-" + chunk.getDocumentId(), k -> new ArrayList<>()).add(chunk);
        }
        return grouped.values().stream().map(group -> {
            var chunk = group.getFirst();
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("documentId", chunk.getDocumentId()); source.put("documentTitle", chunk.getDocumentTitle());
            source.put("content", chunk.getContent()); source.put("score", score(chunk));
            source.put("chunkIndex", chunk.getChunkIndex()); source.put("totalChunks", chunk.getTotalChunks());
            source.put("chunks", group.stream().map(RagEvidence::item).toList());
            source.put("mixedGenerations", mixed(group)); return source;
        }).toList();
    }
    public static String snapshot(List<RankedChunk> chunks, String context) {
        if (chunks == null || chunks.isEmpty()) return null;
        long known = chunks.stream().filter(RagEvidence::generationKnown).count();
        var snapshot = new TreeMap<String, Object>();
        snapshot.put("schemaVersion", 1); snapshot.put("scope", "RETRIEVED_CANDIDATES");
        snapshot.put("generationCoverage", known == chunks.size() ? "COMPLETE" : known == 0 ? "NONE" : "PARTIAL");
        snapshot.put("publicationVersion", "UNPINNED"); snapshot.put("mixedGenerations", mixed(chunks));
        snapshot.put("contextSha256", ContentFingerprint.sha256(context)); snapshot.put("contextCharacters", context == null ? 0 : context.length());
        snapshot.put("chunks", chunks.stream().map(RagEvidence::item).toList());
        return JSON.toJSONString(snapshot);
    }
    private static Map<String, Object> item(RankedChunk chunk) {
        var item = new TreeMap<String, Object>();
        String content = chunk.getContent();
        item.put("documentId", chunk.getDocumentId()); item.put("documentTitle", chunk.getDocumentTitle());
        item.put("vectorId", chunk.getVectorId()); item.put("chunkIndex", chunk.getChunkIndex()); item.put("totalChunks", chunk.getTotalChunks());
        item.put("indexRevision", chunk.getIndexRevision()); item.put("indexedContentSha256", chunk.getIndexedContentSha256());
        item.put("chunkContentSha256", ContentFingerprint.sha256(content));
        item.put("indexedChunkHashVerified", Boolean.TRUE.equals(chunk.getIndexedChunkHashVerified())
                && Objects.equals(chunk.getChunkContentSha256(), ContentFingerprint.sha256(content)));
        item.put("content", content != null && content.length() > 12000 ? content.substring(0, 12000) : content);
        item.put("truncated", content != null && content.length() > 12000); item.put("score", score(chunk));
        return item;
    }
    private static boolean generationKnown(RankedChunk chunk) {
        return chunk.getVectorId() != null && !chunk.getVectorId().isBlank() && chunk.getIndexRevision() != null && chunk.getIndexRevision() > 0
                && chunk.getIndexedContentSha256() != null && chunk.getIndexedContentSha256().matches("[a-f0-9]{64}")
                && Boolean.TRUE.equals(chunk.getIndexedChunkHashVerified())
                && Objects.equals(chunk.getChunkContentSha256(), ContentFingerprint.sha256(chunk.getContent()));
    }
    private static boolean mixed(List<RankedChunk> chunks) {
        Map<Long, Set<String>> versions = new HashMap<>();
        for (var chunk : chunks) {
            if (chunk.getDocumentId() != null && chunk.getIndexRevision() != null) {
                versions.computeIfAbsent(chunk.getDocumentId(), k -> new HashSet<>()).add(chunk.getIndexRevision() + ":" + chunk.getIndexedContentSha256());
            }
        }
        return versions.values().stream().anyMatch(v -> v.size() > 1);
    }
    private static double score(RankedChunk chunk) {
        Double score = chunk.getFinalScore() == null ? chunk.getRelevanceScore() : chunk.getFinalScore();
        return score == null || !Double.isFinite(score) ? 0 : score;
    }
}
