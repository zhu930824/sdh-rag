package cn.sdh.backend.memory.layer;

import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.MemoryEntry;
import cn.sdh.backend.memory.core.MemoryQuery;
import cn.sdh.backend.memory.core.MemoryType;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkingMemoryLayerTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ListOperations<String, String> lists = mock(ListOperations.class);
    private final WorkingMemoryLayer memory = new WorkingMemoryLayer(redis, new MemoryConfig());

    @Test void sameSessionIdCannotExposeAnotherUsersConversation() {
        Map<String, List<String>> data = new HashMap<>();
        when(redis.opsForList()).thenReturn(lists);
        when(lists.rightPush(anyString(), anyString())).thenAnswer(call -> {
            data.computeIfAbsent(call.getArgument(0), ignored -> new ArrayList<>()).add(call.getArgument(1));
            return 1L;
        });
        when(lists.range(anyString(), eq(0L), eq(-1L)))
                .thenAnswer(call -> data.getOrDefault(call.getArgument(0), List.of()));
        memory.store(entry(1L, "shared", "用户一的秘密"));
        memory.store(entry(2L, "shared", "用户二的秘密"));
        List<MemoryEntry> recalled = memory.recall(MemoryQuery.builder().userId(2L).sessionId("shared").build());
        assertEquals(List.of("用户二的秘密"), recalled.stream().map(MemoryEntry::getContent).toList());
        assertEquals("shared", recalled.getFirst().getSessionId());
    }

    @Test void missingUserIdentityNeverReadsLegacySessionOnlyKeys() {
        assertTrue(memory.recall(MemoryQuery.builder().sessionId("shared").build()).isEmpty());
        verifyNoInteractions(redis);
    }

    @Test void clearAllScansOnlyTheSelectedUserNamespace() {
        @SuppressWarnings("unchecked") Cursor<String> keys = mock(Cursor.class);
        when(redis.scan(any(ScanOptions.class))).thenReturn(keys);
        when(keys.hasNext()).thenReturn(true, false);
        when(keys.next()).thenReturn("memory:working:v2:7:c2hhcmVk");
        memory.clearByUserId(7L);
        verify(redis).scan(argThat(options -> "memory:working:v2:7:*".equals(options.getPattern())));
        verify(redis).delete("memory:working:v2:7:c2hhcmVk");
        verify(keys).close();
    }

    private MemoryEntry entry(Long userId, String sessionId, String content) {
        return MemoryEntry.builder().id(UUID.randomUUID().toString()).userId(userId).sessionId(sessionId)
                .content(content).type(MemoryType.EPISODE).createdAt(LocalDateTime.now()).build();
    }
}
