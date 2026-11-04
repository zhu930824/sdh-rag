package cn.sdh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 层级搜索结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    /** 检索是否成功 */
    private boolean success;

    /** 降级提示消息 */
    private String message;

    /** 最终返回的片段 */
    private List<RankedChunk> chunks;

    /** 原始查询 */
    private String originalQuery;

    /** 是否触发降级策略 */
    private boolean fallbackTriggered;

    /** 降级原因 */
    private String fallbackReason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankedChunk {
        private Long documentId;
        private String documentTitle;
        private String documentSummary;
        private String content;
        private Integer chunkIndex;
        private Integer totalChunks;
        private String vectorId;
        private Integer indexRevision;
        private String indexedContentSha256;
        private String chunkContentSha256;
        private Boolean indexedChunkHashVerified;
        private Double relevanceScore;
        private Double freshnessScore;
        private Double finalScore;
        private String department;
        private String docType;
        private LocalDate docDate;
    }

    public static SearchResult empty(String reason) {
        return SearchResult.builder()
            .success(false)
            .message("知识库中未找到与该问题匹配的文档内容。建议：\n1. 更换关键词重新提问\n2. 检查是否有相关文档已上传\n3. 尝试扩大知识库检索范围")
            .fallbackTriggered(true)
            .fallbackReason(reason)
            .chunks(List.of())
            .build();
    }

    public static SearchResult of(List<RankedChunk> chunks, String originalQuery) {
        return SearchResult.builder()
            .success(true)
            .chunks(chunks)
            .originalQuery(originalQuery)
            .fallbackTriggered(false)
            .build();
    }
}
