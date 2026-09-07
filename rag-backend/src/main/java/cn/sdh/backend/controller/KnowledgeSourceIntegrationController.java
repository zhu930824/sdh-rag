package cn.sdh.backend.controller;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.interceptor.KnowledgeSourceApiKeyInterceptor;
import cn.sdh.backend.service.KnowledgeSourceApiKeyService;
import cn.sdh.backend.service.KnowledgeSourceIntegrationService;
import cn.sdh.backend.service.KnowledgeSourceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/integration/knowledge-sources")
public class KnowledgeSourceIntegrationController {
    private final KnowledgeSourceIntegrationService integrationService;

    @PostMapping("/{sourceId}/ingest")
    public ResponseEntity<Result<KnowledgeSourceService.IngestResult>> ingest(
            @PathVariable Long sourceId,
            @RequestBody KnowledgeSourceService.PushCommand command,
            HttpServletRequest request) {
        String requestId = (String) request.getAttribute(KnowledgeSourceApiKeyInterceptor.REQUEST_ID_ATTRIBUTE);
        KnowledgeSourceApiKeyService.AuthenticatedApiKey authentication =
                (KnowledgeSourceApiKeyService.AuthenticatedApiKey) request.getAttribute(KnowledgeSourceApiKeyInterceptor.AUTH_ATTRIBUTE);
        try {
            if (authentication == null) throw new BusinessException(401, "API Key 认证上下文不存在");
            KnowledgeSourceService.IngestResult result = integrationService.ingest(authentication, sourceId, command,
                    requestId, request.getHeader("User-Agent"));
            return ResponseEntity.ok(Result.success("同步内容已接收并进入索引处理队列", result));
        } catch (BusinessException e) {
            int status = e.getCode() != null && e.getCode() >= 400 && e.getCode() < 600 ? e.getCode() : 500;
            return ResponseEntity.status(status).body(Result.error(status, e.getMessage()));
        }
    }
}
