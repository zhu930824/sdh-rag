package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeSourceSyncCoordinatorTest {
    @Mock KnowledgeSourceService sources;
    @Mock KnowledgeSourceSyncLock lock;
    KnowledgeSourceSyncCoordinator coordinator;

    @BeforeEach void setup() { coordinator = new KnowledgeSourceSyncCoordinator(sources, lock); }

    @Test void concurrentManualPullIsRejectedBeforeCallingConnector() {
        when(lock.acquire(3L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> coordinator.pullNow(7L, 3L));

        assertEquals(409, error.getCode());
        verifyNoInteractions(sources);
    }

    @Test void lockIsReleasedWhenPullFails() {
        KnowledgeSourceSyncLock.LockHandle handle = new KnowledgeSourceSyncLock.LockHandle("lock", "owner");
        when(lock.acquire(3L)).thenReturn(handle);
        when(sources.pullNow(7L, 3L)).thenThrow(new BusinessException(502, "upstream failed"));

        assertThrows(BusinessException.class, () -> coordinator.pullNow(7L, 3L));

        verify(lock).release(handle);
    }
}
