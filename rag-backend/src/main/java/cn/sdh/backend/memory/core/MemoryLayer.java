package cn.sdh.backend.memory.core;

import java.util.List;

/**
 * 记忆层接口
 */
public interface MemoryLayer {

    /**
     * 存储记忆
     */
    void store(MemoryEntry entry);

    /**
     * 批量存储记忆
     */
    default void storeAll(List<MemoryEntry> entries) {
        entries.forEach(this::store);
    }

    /**
     * 检索记忆
     */
    List<MemoryEntry> recall(MemoryQuery query);

    /**
     * 更新记忆
     */
    void update(MemoryEntry entry);

    /**
     * 删除记忆
     */
    void delete(String id);

    /**
     * 获取记忆层类型
     */
    MemoryLayerType getType();

    /**
     * 清除用户的所有记忆
     */
    void clearByUserId(Long userId);

    /**
     * 检查该层是否可用
     */
    default boolean isAvailable() {
        return true;
    }
}
