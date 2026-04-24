package cn.sdh.backend.memory.boundary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 边界检测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoundaryResult {

    /** 边界类型 */
    public enum BoundaryType {
        /** 话题切换 */
        TRANSITION,
        /** 长度限制 */
        LENGTH_LIMIT,
        /** 无边界 */
        NONE
    }

    /** 边界类型 */
    private BoundaryType type;

    /** 当前话题标签 */
    private String topicTag;

    /** 原因说明 */
    private String reason;

    /** 是否需要触发摘要 */
    public boolean needsSummarization() {
        return type == BoundaryType.TRANSITION || type == BoundaryType.LENGTH_LIMIT;
    }

    public static BoundaryResult none() {
        return BoundaryResult.builder()
            .type(BoundaryType.NONE)
            .build();
    }

    public static BoundaryResult transition(String topicTag, String reason) {
        return BoundaryResult.builder()
            .type(BoundaryType.TRANSITION)
            .topicTag(topicTag)
            .reason(reason)
            .build();
    }

    public static BoundaryResult lengthLimit() {
        return BoundaryResult.builder()
            .type(BoundaryType.LENGTH_LIMIT)
            .reason("达到最大消息数限制")
            .build();
    }
}
