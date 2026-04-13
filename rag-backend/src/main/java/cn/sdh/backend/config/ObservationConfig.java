package cn.sdh.backend.config;

import cn.sdh.backend.advisor.common.AIModelObservationHandler;
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

    private final AIModelObservationHandler aiModelObservationHandler;

    /**
     * 配置观测注册表
     */
    @Bean
    public ObservationRegistry observationRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(aiModelObservationHandler);
        log.info("ObservationRegistry 已配置 AIModelObservationHandler");
        return registry;
    }
}
