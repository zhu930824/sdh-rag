package cn.sdh.backend.interceptor;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.service.KnowledgeSourceApiKeyService;
import cn.sdh.backend.service.KnowledgeSourceRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KnowledgeSourceApiKeyInterceptor implements HandlerInterceptor {
    public static final String AUTH_ATTRIBUTE = KnowledgeSourceApiKeyInterceptor.class.getName() + ".authentication";
    public static final String REQUEST_ID_ATTRIBUTE = KnowledgeSourceApiKeyInterceptor.class.getName() + ".requestId";
    public static final String API_KEY_HEADER = "X-SDH-API-Key";
    private final KnowledgeSourceApiKeyService apiKeyService;
    private final KnowledgeSourceRateLimiter rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader("X-Request-Id", requestId);
        try {
            String rawKey = request.getHeader(API_KEY_HEADER);
            if (!StringUtils.hasText(rawKey)) throw new BusinessException(401, "缺少 X-SDH-API-Key 请求头");
            KnowledgeSourceApiKeyService.AuthenticatedApiKey authentication = apiKeyService.authenticate(rawKey.trim());
            KnowledgeSourceRateLimiter.Decision decision = rateLimiter.acquire(authentication.id(), authentication.rateLimitPerMinute());
            response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
            if (!decision.allowed()) {
                response.setHeader("Retry-After", "60");
                apiKeyService.recordAudit(authentication, requestId, 0, null, "FAILED", 429,
                        "API Key 调用频率超过限制", 0, request.getHeader("User-Agent"));
                writeError(response, 429, "API Key 调用频率超过限制，请稍后重试");
                return false;
            }
            UserContext context = new UserContext();
            context.setUserId(authentication.createdBy());
            context.setUsername("integration:" + authentication.name());
            UserContext.setUserContext(context);
            request.setAttribute(AUTH_ATTRIBUTE, authentication);
            return true;
        } catch (BusinessException e) {
            int status = e.getCode() != null && e.getCode() >= 400 && e.getCode() < 600 ? e.getCode() : 401;
            writeError(response, status, e.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String safeMessage = message == null ? "认证失败" : message.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + safeMessage + "\",\"data\":null}");
    }
}
