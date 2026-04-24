package cn.sdh.backend.memory.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 记忆条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryEntry {

    /** 唯一标识 */
    private String id;

    /** 用户ID */
    private Long userId;

    /** 会话ID */
    private String sessionId;

    /** 记忆层 */
    private MemoryLayerType layer;

    /** 记忆类型 */
    private MemoryType type;

    /** 记忆内容 */
    private String content;

    /** 向量嵌入 */
    private float[] embedding;

    /** 元数据 */
    private Map<String, Object> metadata;

    /** 重要性评分 1-10 */
    private int importance;

    /** 访问次数 */
    private int accessCount;

    /** 最后访问时间 */
    private LocalDateTime lastAccessAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /**
     * 计算衰减后的重要性
     */
    public double getDecayedImportance() {
        if (lastAccessAt == null) {
            return importance;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(lastAccessAt, LocalDateTime.now());
        double recencyFactor = Math.exp(-0.05 * days);
        double accessFactor = 1 + Math.log(1 + accessCount) / 10.0;
        return importance * recencyFactor * accessFactor;
    }
}
