package cn.sdh.backend.config;

import cn.sdh.backend.rag.AIModelObservationHandler;
import cn.sdh.backend.service.TokenUsageService;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 可观测性配置
 * 配置 Spring AI 的观测处理器
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ObservationConfig {

    private final TokenUsageService tokenUsageService;

    /**
     * 注册 AI 模型观测处理器
     */
    @Bean
    public AIModelObservationHandler aiModelObservationHandler() {
        log.info("初始化 AIModelObservationHandler");
        return new AIModelObservationHandler(tokenUsageService);
    }

    /**
     * 配置观测注册表
     */
    @Bean
    public ObservationRegistry observationRegistry(AIModelObservationHandler handler) {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(handler);
        log.info("ObservationRegistry 已配置 AIModelObservationHandler");
        return registry;
    }
}
