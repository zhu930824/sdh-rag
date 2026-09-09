package cn.sdh.backend.job;

import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.service.KnowledgeSourceService;
import cn.sdh.backend.service.KnowledgeSourceSyncCoordinator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgeSourcePullJobHandler implements PlatformJobHandler {
    public static final String TYPE = "KNOWLEDGE_SOURCE_PULL";

    private final KnowledgeSourceSyncCoordinator coordinator;
    private final ObjectMapper objectMapper;

    public record Payload(Long sourceId) {}

    @Override
    public String jobType() {
        return TYPE;
    }

    @Override
    public String execute(PlatformJob job, PlatformJobExecutionContext context) throws Exception {
        Payload payload = objectMapper.readValue(job.getPayloadJson(), Payload.class);
        if (payload.sourceId() == null) throw new IllegalArgumentException("知识源 ID 不能为空");
        context.checkpoint();
        KnowledgeSourceService.IngestResult result = coordinator.pullScheduled(payload.sourceId());
        if (result == null) return "同一知识源已有同步运行，本轮安全跳过";
        return "同步完成，扫描 " + result.scanned() + " 条，新增 " + result.created()
                + " 条，更新 " + result.updated() + " 条";
    }
}
