package cn.sdh.backend.memory.core;

/**
 * 记忆类型枚举
 */
public enum MemoryType {
    PREFERENCE("preference", "用户偏好"),
    FACT("fact", "知识事实"),
    PATTERN("pattern", "行为模式"),
    EPISODE("episode", "对话片段"),
    SUMMARY("summary", "对话摘要");

    private final String code;
    private final String name;

    MemoryType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
}
