package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.mapper.KnowledgeBaseMapper;
import cn.sdh.backend.mapper.KnowledgeBaseMemberMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeAccessServiceTest {
    @Mock KnowledgeBaseMapper bases;
    @Mock KnowledgeBaseMemberMapper members;
    @Mock KnowledgeDocumentMapper documents;
    @Mock KnowledgeDocumentRelationMapper relations;
    KnowledgeAccessService access;

    @BeforeEach void setup() {
        access = new KnowledgeAccessService(bases, members, documents, relations);
    }

    @Test void ownerCanManageAndPublicUserCanOnlyRead() {
        KnowledgeBase base = base(7L, 1L, true);
        when(bases.selectById(7L)).thenReturn(base);

        assertSame(base, access.requireOwner(7L, 1L));
        assertSame(base, access.requireRead(7L, 2L));
        BusinessException denied = assertThrows(BusinessException.class, () -> access.requireEdit(7L, 2L));
        assertEquals(403, denied.getCode());
    }

    @Test void editorCanEditAndReaderCannot() {
        KnowledgeBase base = base(7L, 1L, false);
        when(bases.selectById(7L)).thenReturn(base);
        when(members.permission(7L, 2L)).thenReturn("editor");
        when(members.permission(7L, 3L)).thenReturn("reader");

        assertEquals("editor", access.role(base, 2L));
        assertSame(base, access.requireEdit(7L, 2L));
        assertThrows(BusinessException.class, () -> access.requireEdit(7L, 3L));
        assertSame(base, access.requireRead(7L, 3L));
    }

    @Test void revokedPrivateMemberLosesReadAccess() {
        KnowledgeBase base = base(7L, 1L, false);
        when(bases.selectById(7L)).thenReturn(base);
        when(members.permission(7L, 2L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> access.requireRead(7L, 2L));
    }

    @Test void linkedDocumentInheritsReadableKnowledgeBase() {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(9L);
        doc.setUserId(1L);
        when(documents.selectById(9L)).thenReturn(doc);
        when(relations.selectKnowledgeIdsByDocumentId(9L)).thenReturn(List.of(7L));
        KnowledgeBase base = base(7L, 1L, false);
        when(bases.selectById(7L)).thenReturn(base);
        when(members.permission(7L, 2L)).thenReturn("reader");

        assertSame(doc, access.requireDocumentRead(9L, 2L));
        assertThrows(BusinessException.class, () -> access.requireDocumentOwner(9L, 2L));
    }

    @Test void anonymousActorIsRejectedBeforeDatabaseAccess() {
        BusinessException denied = assertThrows(BusinessException.class, () -> access.requireRead(7L, null));
        assertEquals(401, denied.getCode());
        verifyNoInteractions(bases, members, documents, relations);
    }

    private KnowledgeBase base(Long id, Long owner, boolean isPublic) {
        KnowledgeBase base = new KnowledgeBase();
        base.setId(id);
        base.setUserId(owner);
        base.setIsPublic(isPublic);
        return base;
    }
}
