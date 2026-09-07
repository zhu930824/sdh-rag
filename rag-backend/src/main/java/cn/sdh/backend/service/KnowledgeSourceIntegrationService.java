package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgeSourceIntegrationService {
    private final KnowledgeSourceService sourceService;
    private final KnowledgeSourceApiKeyService apiKeyService;

    public KnowledgeSourceService.IngestResult ingest(KnowledgeSourceApiKeyService.AuthenticatedApiKey authentication,
                                                       Long requestedSourceId,
                                                       KnowledgeSourceService.PushCommand command,
                                                       String requestId,
                                                       String userAgent) {
        long started = System.nanoTime();
        int itemCount = command == null || command.items() == null ? 0 : command.items().size();
        try {
            if (!authentication.sourceId().equals(requestedSourceId)) {
                throw new BusinessException(403, "API Key 无权访问该数据源");
            }
            KnowledgeSourceService.IngestResult result = sourceService.ingest(
                    authentication.knowledgeId(), authentication.sourceId(), command);
            apiKeyService.recordAudit(authentication, requestId, itemCount, result.taskId(), "SUCCESS", 200,
                    null, elapsedMillis(started), userAgent);
            return result;
        } catch (BusinessException e) {
            apiKeyService.recordAudit(authentication, requestId, itemCount, null, "FAILED", normalizeCode(e.getCode()),
                    e.getMessage(), elapsedMillis(started), userAgent);
            throw e;
        } catch (Exception e) {
            apiKeyService.recordAudit(authentication, requestId, itemCount, null, "FAILED", 500,
                    "系统内部错误", elapsedMillis(started), userAgent);
            throw e;
        }
    }

    private int normalizeCode(Integer code) { return code != null && code >= 400 && code < 600 ? code : 500; }
    private long elapsedMillis(long started) { return (System.nanoTime() - started) / 1_000_000; }
}
