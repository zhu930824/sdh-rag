package cn.sdh.backend.agent;

import cn.sdh.backend.service.factory.ModelProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Agent 模型能力判定:仅白名单供应商开放 LLM 工具调用(其余供应商未经验证,静默降级会造成认知错位,
 * 统一在 ask 前返回显式错误事件)。
 */
@Slf4j
@Component
public class AgentCapability {

    private final Set<ModelProvider> supported = new HashSet<>();

    public AgentCapability(@Value("${xushu.agent.supported-providers:dashscope,openai}") String providers) {
        if (providers != null && !providers.isBlank()) {
            for (String item : providers.split(",")) {
                String trimmed = item.trim().toLowerCase(Locale.ROOT);
                ModelProvider provider = Arrays.stream(ModelProvider.values())
                        .filter(p -> p.getCode().equals(trimmed) || p.getAliases().contains(trimmed))
                        .findFirst()
                        .orElse(null);
                if (provider != null) {
                    supported.add(provider);
                    log.info("Agent 工具调用白名单供应商: {}", provider);
                }
            }
        }
        log.info("Agent 工具调用白名单(可配置 xushu.agent.supported-providers): {}", supported);
    }

    public boolean supports(ModelProvider provider) {
        return provider != null && supported.contains(provider);
    }

    public Set<ModelProvider> supported() {
        return Set.copyOf(supported);
    }
}
