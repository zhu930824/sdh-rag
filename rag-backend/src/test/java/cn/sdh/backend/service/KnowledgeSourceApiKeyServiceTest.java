package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.KnowledgeSource;
import cn.sdh.backend.entity.KnowledgeSourceApiKey;
import cn.sdh.backend.mapper.KnowledgeSourceApiAuditMapper;
import cn.sdh.backend.mapper.KnowledgeSourceApiKeyMapper;
import cn.sdh.backend.mapper.KnowledgeSourceMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeSourceApiKeyServiceTest {
    @Mock KnowledgeSourceApiKeyMapper apiKeys;
    @Mock KnowledgeSourceApiAuditMapper audits;
    @Mock KnowledgeSourceMapper sources;
    @Mock KnowledgeAccessService access;
    KnowledgeSourceApiKeyService service;

    @BeforeEach void setup() {
        UserContext context = new UserContext();
        context.setUserId(8L);
        UserContext.setUserContext(context);
        service = new KnowledgeSourceApiKeyService(apiKeys, audits, sources, access);
    }

    @AfterEach void clear() { UserContext.clear(); }

    @Test void createReturnsSecretOnceAndStoresOnlyHash() {
        KnowledgeSource source = source();
        when(sources.selectById(3L)).thenReturn(source);
        when(apiKeys.selectCount(any())).thenReturn(0L);
        AtomicReference<KnowledgeSourceApiKey> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            KnowledgeSourceApiKey key = invocation.getArgument(0);
            key.setId(21L); stored.set(key); return 1;
        }).when(apiKeys).insert(any(KnowledgeSourceApiKey.class));

        var created = service.create(7L, 3L,
                new KnowledgeSourceApiKeyService.ApiKeyCommand("生产工单", 120, null));

        assertEquals(21L, created.id());
        assertTrue(created.apiKey().startsWith(created.keyPrefix() + "."));
        assertEquals(64, stored.get().getKeyHash().length());
        assertNotEquals(created.apiKey(), stored.get().getKeyHash());
    }

    @Test void authenticateUsesFullSecretAndRejectsLookalike() {
        KnowledgeSource source = source();
        when(sources.selectById(3L)).thenReturn(source);
        when(apiKeys.selectCount(any())).thenReturn(0L);
        AtomicReference<KnowledgeSourceApiKey> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            KnowledgeSourceApiKey key = invocation.getArgument(0);
            key.setId(22L); stored.set(key); return 1;
        }).when(apiKeys).insert(any(KnowledgeSourceApiKey.class));
        var created = service.create(7L, 3L,
                new KnowledgeSourceApiKeyService.ApiKeyCommand("ERP", 30, null));
        when(apiKeys.selectOne(any())).thenReturn(stored.get());
        when(apiKeys.update((KnowledgeSourceApiKey) isNull(), any(UpdateWrapper.class))).thenReturn(1);

        var authenticated = service.authenticate(created.apiKey());

        assertEquals(22L, authenticated.id());
        assertEquals(3L, authenticated.sourceId());
        assertEquals(30, authenticated.rateLimitPerMinute());
        assertThrows(BusinessException.class, () -> service.authenticate(created.keyPrefix() + ".wrong-secret"));
    }

    @Test void expiredAndRevokedKeysAreRejected() {
        KnowledgeSourceApiKey key = new KnowledgeSourceApiKey();
        key.setStatus(1); key.setKeyPrefix("sdh_expired1234"); key.setKeyHash("0".repeat(64));
        key.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(apiKeys.selectOne(any())).thenReturn(key);
        assertEquals(401, assertThrows(BusinessException.class,
                () -> service.authenticate("sdh_expired1234.secret")).getCode());

        key.setStatus(0); key.setExpiresAt(null);
        assertEquals(401, assertThrows(BusinessException.class,
                () -> service.authenticate("sdh_expired1234.secret")).getCode());
    }

    private KnowledgeSource source() {
        KnowledgeSource source = new KnowledgeSource();
        source.setId(3L); source.setKnowledgeId(7L); source.setSourceType("api_push");
        source.setCreatedBy(8L); source.setStatus(1);
        return source;
    }
}
