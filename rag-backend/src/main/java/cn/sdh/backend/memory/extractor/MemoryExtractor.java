package cn.sdh.backend.memory.extractor;

import cn.sdh.backend.memory.core.MemoryEntry;
import cn.sdh.backend.memory.core.MemoryLayerType;
import cn.sdh.backend.memory.core.MemoryType;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 记忆提取器接口
 */
public interface MemoryExtractor {

    /**
     * 从对话中提取记忆
     */
    CompletableFuture<List<MemoryEntry>> extract(Long userId, String question, String answer);

    /**
     * 获取提取器处理的记忆类型
     */
    MemoryType getMemoryType();

    /**
     * 获取目标存储层
     */
    MemoryLayerType getTargetLayer();

    /**
     * 获取提取器名称
     */
    default String getName() {
        return getMemoryType().getName() + "Extractor";
    }
}
