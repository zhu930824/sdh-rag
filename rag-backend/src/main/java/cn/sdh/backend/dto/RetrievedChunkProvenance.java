package cn.sdh.backend.dto;

import org.springframework.ai.document.Document;
import cn.sdh.backend.utils.ContentFingerprint;

/** Observed vector-generation metadata, never the latest mutable document version. */
public record RetrievedChunkProvenance(String vectorId, String indexGeneration, Integer indexRevision, String indexedContentSha256,
                                       String chunkContentSha256, boolean indexedChunkHashVerified) {
    public static RetrievedChunkProvenance from(Document document) {
        var metadata = document.getMetadata();
        String actual = ContentFingerprint.sha256(document.getText());
        String expected = digest(metadata.get("chunk_content_sha256"));
        return new RetrievedChunkProvenance(metadata.get("id") == null ? null : metadata.get("id").toString(),
                java.util.Objects.toString(metadata.get("index_generation"), null),
                positiveRevision(metadata.get("index_revision")), digest(metadata.get("indexed_content_sha256")),
                actual, expected != null && expected.equals(actual));
    }
    private static Integer positiveRevision(Object value) {
        try { int number = new java.math.BigDecimal(String.valueOf(value)).intValueExact(); return number > 0 ? number : null; }
        catch (NumberFormatException | ArithmeticException invalid) { return null; }
    }
    private static String digest(Object value) {
        return value instanceof String text && text.matches("[a-fA-F0-9]{64}") ? text.toLowerCase(java.util.Locale.ROOT) : null;
    }
    public void apply(SearchResult.RankedChunk chunk) {
        chunk.setVectorId(vectorId); chunk.setIndexRevision(indexRevision); chunk.setIndexedContentSha256(indexedContentSha256);
        chunk.setIndexGeneration(indexGeneration);
        chunk.setChunkContentSha256(chunkContentSha256); chunk.setIndexedChunkHashVerified(indexedChunkHashVerified);
    }
}
