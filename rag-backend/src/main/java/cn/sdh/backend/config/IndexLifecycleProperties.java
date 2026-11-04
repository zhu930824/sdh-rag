package cn.sdh.backend.config;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data @Component @Validated
@ConfigurationProperties(prefix = "knowledge.index-lifecycle")
public class IndexLifecycleProperties {
    private boolean cleanupEnabled = false;
    @Min(1) @Max(3600) private int readLeaseSeconds = 300;
    @Min(0) private int retentionSeconds = 604800;
    @Min(1) private int cleanupLeaseSeconds = 300;
    @Min(1) private int retrySeconds = 60;
    @Min(1) private int resweepSeconds = 86400;
    @Min(1) @Max(100) private int batchSize = 20;
    @Min(1) @Max(100) private int knowledgeBatchSize = 10;
}
