package cn.sdh.backend.knowledge;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.knowledge.connector.KnowledgeSourceConnector;
import cn.sdh.backend.knowledge.connector.KnowledgeSourceConnectorRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeSourceConnectorRegistryTest {
    @Test void connectorTypesAreNormalizedAndDiscoverable() {
        KnowledgeSourceConnector connector = mock(KnowledgeSourceConnector.class);
        when(connector.type()).thenReturn(" SharePoint ");
        KnowledgeSourceConnectorRegistry registry = new KnowledgeSourceConnectorRegistry(List.of(connector));
        assertSame(connector, registry.require("sharepoint"));
        assertEquals(Set.of("sharepoint"), registry.supportedTypes());
    }

    @Test void unknownConnectorIsRejected() {
        KnowledgeSourceConnectorRegistry registry = new KnowledgeSourceConnectorRegistry(List.of());
        assertThrows(BusinessException.class, () -> registry.require("unknown"));
    }
}
