package cn.sdh.backend.job;

import cn.sdh.backend.dto.DocumentLinkConfig;
import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.service.DocumentProcessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIndexJobHandlerTest {
    @Mock DocumentProcessService documentProcessService;
    @Mock PlatformJobExecutionContext context;

    @Test
    void validatesPayloadAndDelegatesToReliableProcessor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DocumentIndexJobPayload payload = new DocumentIndexJobPayload(
                3L, 4L, 5L, 2, new DocumentLinkConfig());
        PlatformJob job = new PlatformJob();
        job.setId(9L);
        job.setPayloadJson(mapper.writeValueAsString(payload));
        when(documentProcessService.executeIndexJob(payload, 9L, context)).thenReturn("索引完成");

        String result = new DocumentIndexJobHandler(documentProcessService, mapper).execute(job, context);

        assertEquals("索引完成", result);
        verify(context).checkpoint();
        verify(documentProcessService).executeIndexJob(payload, 9L, context);
    }

    @Test
    void terminalFailureCleansIncompleteGeneration() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DocumentIndexJobPayload payload = new DocumentIndexJobPayload(
                3L, 4L, 5L, 2, new DocumentLinkConfig());
        PlatformJob job = new PlatformJob();
        job.setId(9L);
        job.setAttempts(10);
        job.setMaxAttempts(10);
        job.setPayloadJson(mapper.writeValueAsString(payload));
        when(documentProcessService.executeIndexJob(payload, 9L, context))
                .thenThrow(new IllegalStateException("embedding unavailable"));

        DocumentIndexJobHandler handler = new DocumentIndexJobHandler(documentProcessService, mapper);
        assertThrows(IllegalStateException.class, () -> handler.execute(job, context));

        verify(documentProcessService).abandonIndexJob(payload, 9L);
    }
}
