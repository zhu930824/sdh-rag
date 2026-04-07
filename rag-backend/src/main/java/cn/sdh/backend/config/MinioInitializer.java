package cn.sdh.backend.config;

import cn.sdh.backend.service.MinioService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MinIO 初始化器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioInitializer {

    private final MinioService minioService;

    @PostConstruct
    public void init() {
        try {
            minioService.initBucket();
            log.info("MinIO 初始化完成");
        } catch (Exception e) {
            log.warn("MinIO 初始化失败: {}", e.getMessage());
        }
    }
}
