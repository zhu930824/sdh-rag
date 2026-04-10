package cn.sdh.backend.service.factory;

import lombok.Getter;

import java.util.Arrays;
import java.util.Set;

/**
 * 模型提供者枚举
 * 统一管理各种模型服务商的配置
 */
@Getter
public enum ModelProvider {

    OPENAI(
            "openai",
            "https://api.openai.com",
            Set.of("openai", "open_ai")
    ),
    DASHSCOPE(
            "dashscope",
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            Set.of("dashscope", "alibaba", "qwen", "tongyi")
    ),
    DEEPSEEK(
            "deepseek",
            "https://api.deepseek.com",
            Set.of("deepseek", "deep_seek")
    ),
    MOONSHOT(
            "moonshot",
            "https://api.moonshot.cn/v1",
            Set.of("moonshot", "kimichat", "kimi")
    ),
    SILICON(
            "silicon",
            "https://api.siliconflow.cn/v1",
            Set.of("silicon", "siliconflow")
    ),
    ZHIPU(
            "zhipu",
            "https://open.bigmodel.cn/api/paas/v4",
            Set.of("zhipu", "zhipuai", "glm")
    ),
    BAICHUAN(
            "baichuan",
            "https://api.baichuan-ai.com/v1",
            Set.of("baichuan")
    ),
    MINIMAX(
            "minimax",
            "https://api.minimax.chat/v1",
            Set.of("minimax")
    ),
    LOCAL(
            "local",
            "http://localhost:11434/v1",
            Set.of("local", "ollama", "localhost")
    ),
    CUSTOM(
            "custom",
            "",
            Set.of("custom")
    );

    private final String code;
    private final String defaultBaseUrl;
    private final Set<String> aliases;

    ModelProvider(String code, String defaultBaseUrl, Set<String> aliases) {
        this.code = code;
        this.defaultBaseUrl = defaultBaseUrl;
        this.aliases = aliases;
    }

    /**
     * 根据字符串解析模型提供者
     * @param value 提供者名称或别名
     * @return ModelProvider，未找到返回 null
     */
    public static ModelProvider fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        String lowerValue = value.toLowerCase().trim();

        return Arrays.stream(values())
                .filter(provider -> provider.aliases.contains(lowerValue))
                .findFirst()
                .orElse(null);
    }

    /**
     * 判断是否支持 Rerank 功能
     */
    public boolean supportsRerank() {
        return this == DASHSCOPE;
    }

    /**
     * 判断是否为国内服务商
     */
    public boolean isChineseProvider() {
        return this == DASHSCOPE || this == DEEPSEEK || this == MOONSHOT ||
               this == SILICON || this == ZHIPU || this == BAICHUAN || this == MINIMAX;
    }
}
