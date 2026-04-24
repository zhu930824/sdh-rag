package cn.sdh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 记忆概览响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryOverviewResponse {

    /** 用户偏好列表 */
    private List<MemoryItem> preferences;

    /** 知识事实列表 */
    private List<MemoryItem> facts;

    /** 行为模式列表 */
    private List<MemoryItem> patterns;

    /** 最近摘要列表 */
    private List<MemoryItem> recentSummaries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryItem {
        private String id;
        private String type;
        private String content;
        private Integer importance;
        private String createdAt;
        private Integer accessCount;
    }
}
