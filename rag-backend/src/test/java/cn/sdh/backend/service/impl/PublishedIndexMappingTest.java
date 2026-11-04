package cn.sdh.backend.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublishedIndexMappingTest {
    @Test @SuppressWarnings("unchecked")
    void aFailedGenerationMappingMustStopTheWriterAndBeRetried() throws Exception {
        var es = mock(ElasticsearchClient.class); var indices = mock(ElasticsearchIndicesClient.class);
        when(es.indices()).thenReturn(indices);
        when(indices.exists(any(Function.class))).thenReturn(new BooleanResponse(true));
        when(indices.putMapping(any(Function.class))).thenThrow(new IOException("mapping unavailable"));
        var service = new ElasticsearchIndexServiceImpl(es);
        assertThrows(IllegalStateException.class,() -> service.ensureIndexExists(7L,"model"));
        assertThrows(IllegalStateException.class,() -> service.ensureIndexExists(7L,"model"));
        verify(indices,times(2)).putMapping(any(Function.class));
    }
}
