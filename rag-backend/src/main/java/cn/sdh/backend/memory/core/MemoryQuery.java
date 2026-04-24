package cn.sdh.backend.memory.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 记忆查询对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryQuery {

    /** 用户ID */
    private Long userId;

    /** 会话ID */
    private String sessionId;

    /** 查询文本 */
    private String query;

    /** 查询向量 */
    private float[] queryEmbedding;

    /** 记忆类型过滤 */
    private MemoryType type;

    /** 返回数量限制 */
    private int limit;

    /** 最小相似度阈值 */
    private double minSimilarity;

    /** 是否包含向量 */
    private boolean includeEmbedding;
}
