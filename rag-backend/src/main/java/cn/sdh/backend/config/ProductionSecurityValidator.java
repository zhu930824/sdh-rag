package cn.sdh.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * Fails fast when a production instance is started with missing or weak secrets.
 */
@Component
@RequiredArgsConstructor
public class ProductionSecurityValidator {

    private static final Set<String> WEAK_VALUES = Set.of(
            "admin", "root", "password", "secret", "changeme", "default", "minioadmin", "test"
    );

    private final Environment environment;

    @PostConstruct
    void validate() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))
                || !environment.getProperty("security.startup-validation.enabled", Boolean.class, true)) {
            return;
        }

        requireStrong("spring.datasource.password", 12);
        requireStrong("spring.neo4j.authentication.password", 12);
        requireStrong("minio.access-key", 8);
        requireStrong("minio.secret-key", 16);
        String adminSecret = requireStrong("xushu.jwt.admin-secret-key", 32);
        String userSecret = requireStrong("xushu.jwt.user-secret-key", 32);
        if (adminSecret.equals(userSecret)) {
            throw new IllegalStateException("生产环境 JWT_ADMIN_SECRET 与 JWT_USER_SECRET 必须不同");
        }
    }

    private String requireStrong(String property, int minimumLength) {
        String value = environment.getProperty(property);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("生产环境缺少必需的安全配置: " + property);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (value.contains("${") || value.length() < minimumLength || WEAK_VALUES.contains(normalized)) {
            throw new IllegalStateException("生产环境安全配置过弱: " + property);
        }
        return value;
    }
}
