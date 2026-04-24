package cn.sdh.backend.memory.core;

/**
 * 记忆层类型枚举
 */
public enum MemoryLayerType {
    WORKING("working", "工作记忆", "当前会话上下文"),
    EPISODIC("episodic", "情景记忆", "完整对话记录"),
    SEMANTIC("semantic", "语义记忆", "用户偏好和事实"),
    PATTERN("pattern", "模式记忆", "用户行为模式"),
    ABSTRACT("abstract", "抽象记忆", "对话摘要");

    private final String code;
    private final String name;
    private final String description;

    MemoryLayerType(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
