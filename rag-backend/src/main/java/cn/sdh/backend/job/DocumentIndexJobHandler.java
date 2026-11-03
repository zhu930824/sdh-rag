package cn.sdh.backend.job;

import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.service.DocumentProcessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentIndexJobHandler implements PlatformJobHandler {
    public static final String TYPE = "DOCUMENT_INDEX";

    private final DocumentProcessService documentProcessService;
    private final ObjectMapper objectMapper;

    @Override
    public String jobType() {
        return TYPE;
    }

    @Override
    public String execute(PlatformJob job, PlatformJobExecutionContext context) throws Exception {
        DocumentIndexJobPayload payload = objectMapper.readValue(job.getPayloadJson(), DocumentIndexJobPayload.class);
        if (payload.relationId() == null || payload.documentId() == null || payload.knowledgeId() == null
                || payload.revision() == null) {
            throw new IllegalArgumentException("文档索引任务参数不完整");
        }
        context.checkpoint();
        try {
            return documentProcessService.executeIndexJob(payload, job.getId(), context);
        } catch (PlatformJobCancelledException cancelled) {
            documentProcessService.abandonIndexJob(payload, job.getId());
            throw cancelled;
        } catch (Exception failure) {
            if (job.getAttempts() != null && job.getMaxAttempts() != null
                    && job.getAttempts() >= job.getMaxAttempts()) {
                documentProcessService.abandonIndexJob(payload, job.getId());
            }
            throw failure;
        }
    }
}
