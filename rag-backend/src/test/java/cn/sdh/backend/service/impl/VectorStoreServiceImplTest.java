package cn.sdh.backend.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class VectorStoreServiceImplTest {
    @Test
    void vectorIdsAreStablePerKnowledgeDocumentAndChunk() {
        String first = VectorStoreServiceImpl.deterministicVectorId(1L, 2L, 3, 4);

        assertEquals(first, VectorStoreServiceImpl.deterministicVectorId(1L, 2L, 3, 4));
        assertNotEquals(first, VectorStoreServiceImpl.deterministicVectorId(1L, 2L, 4, 4));
        assertNotEquals(first, VectorStoreServiceImpl.deterministicVectorId(2L, 2L, 3, 4));
        assertNotEquals(first, VectorStoreServiceImpl.deterministicVectorId(1L, 2L, 3, 5));
    }
}
