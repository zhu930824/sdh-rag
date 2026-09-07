package cn.sdh.backend.knowledge.connector;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Adapter contract for incremental ingestion from business systems. */
public interface KnowledgeSourceConnector {
    enum Mode { PULL, PUSH }

    String type();

    Mode mode();

    void validate(Map<String, Object> configuration);

    FetchResult fetch(FetchRequest request);

    record FetchRequest(
            Map<String, Object> configuration,
            String credential,
            String cursor,
            Instant changedSince,
            int limit) {}

    record SourceItem(
            String externalId,
            String title,
            String content,
            String sourceUrl,
            Instant sourceUpdatedAt,
            Map<String, Object> metadata,
            boolean deleted) {}

    record FetchResult(List<SourceItem> items, String nextCursor, boolean hasMore) {}
}
