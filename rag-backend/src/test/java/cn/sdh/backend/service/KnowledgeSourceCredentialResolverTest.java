package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeSourceCredentialResolverTest {
    @Test void resolvesExternalizedSecretAndRejectsUnsupportedReferences() {
        MockEnvironment environment = new MockEnvironment().withProperty("BUSINESS_API_TOKEN", "secret-value");
        KnowledgeSourceCredentialResolver resolver = new KnowledgeSourceCredentialResolver(environment);

        assertEquals("secret-value", resolver.resolve("env:BUSINESS_API_TOKEN"));
        assertNull(resolver.resolve(null));
        assertThrows(BusinessException.class, () -> resolver.resolve("vault:path/to/key"));
        assertThrows(BusinessException.class, () -> resolver.resolve("env:MISSING_TOKEN"));
    }
}
