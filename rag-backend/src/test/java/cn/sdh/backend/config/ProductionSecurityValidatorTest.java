package cn.sdh.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSecurityValidatorTest {

    @Test
    void ignoresNonProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        assertDoesNotThrow(() -> new ProductionSecurityValidator(environment).validate());
    }

    @Test
    void rejectsMissingProductionSecrets() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class,
                () -> new ProductionSecurityValidator(environment).validate());
    }

    @Test
    void acceptsStrongDistinctProductionSecrets() {
        MockEnvironment environment = productionEnvironment();

        assertDoesNotThrow(() -> new ProductionSecurityValidator(environment).validate());
    }

    @Test
    void rejectsSharedJwtSecret() {
        MockEnvironment environment = productionEnvironment();
        String shared = "shared-jwt-secret-with-more-than-32-characters";
        environment.setProperty("xushu.jwt.admin-secret-key", shared);
        environment.setProperty("xushu.jwt.user-secret-key", shared);

        assertThrows(IllegalStateException.class,
                () -> new ProductionSecurityValidator(environment).validate());
    }

    private MockEnvironment productionEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.datasource.password", "database-password-2026");
        environment.setProperty("spring.neo4j.authentication.password", "neo4j-password-2026");
        environment.setProperty("minio.access-key", "rag-storage-user");
        environment.setProperty("minio.secret-key", "minio-secret-value-2026");
        environment.setProperty("xushu.jwt.admin-secret-key", "admin-jwt-secret-with-more-than-32-characters");
        environment.setProperty("xushu.jwt.user-secret-key", "user-jwt-secret-with-more-than-32-characters");
        return environment;
    }
}
