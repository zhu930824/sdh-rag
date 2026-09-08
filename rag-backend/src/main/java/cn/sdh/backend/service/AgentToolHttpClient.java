package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.AgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class AgentToolHttpClient {
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REQUEST_BYTES = 256 * 1024;
    private final ObjectMapper objectMapper;
    private final KnowledgeSourceCredentialResolver credentialResolver;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public record ToolResponse(int statusCode, String body) {}

    public void validateDefinition(AgentTool tool) {
        URI uri = parseUri(tool.getEndpoint());
        // 内置演示工具 internal:// 直调不经过 HTTP/SSRF 层
        boolean internal = uri.getScheme() != null && "internal".equalsIgnoreCase(uri.getScheme());
        boolean allowHttp = Integer.valueOf(1).equals(tool.getAllowInsecureHttp());
        if (!internal && !"https".equalsIgnoreCase(uri.getScheme())
                && !(allowHttp && "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new BusinessException(400, "业务工具必须使用 HTTPS；测试 HTTP 需显式启用");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new BusinessException(400, "工具 endpoint 地址格式不正确");
        }
        String method = normalizedMethod(tool.getHttpMethod());
        tool.setHttpMethod(method);
        String authType = normalize(tool.getAuthType(), "none").toLowerCase(Locale.ROOT);
        if (!List.of("none", "bearer", "header").contains(authType)) {
            throw new BusinessException(400, "authType 仅支持 none、bearer 或 header");
        }
        if ("header".equals(authType)) validateHeaderName(tool.getAuthHeader());
        tool.setAuthType(authType);
        int timeout = tool.getTimeoutSeconds() == null ? 30 : tool.getTimeoutSeconds();
        if (timeout < 3 || timeout > 120) throw new BusinessException(400, "超时时间必须在 3-120 秒之间");
        tool.setTimeoutSeconds(timeout);
        validateCredentialRef(tool.getCredentialRef());
    }

    public ToolResponse execute(AgentTool tool, Map<String, Object> parameters) {
        validateDefinition(tool);
        URI endpoint = parseUri(tool.getEndpoint());
        validateNetworkTarget(endpoint, Integer.valueOf(1).equals(tool.getAllowPrivateNetwork()));
        Map<String, Object> safeParameters = parameters == null ? Map.of() : parameters;
        String method = tool.getHttpMethod();
        HttpRequest.Builder builder;
        try {
            if (List.of("GET", "DELETE").contains(method)) {
                builder = HttpRequest.newBuilder(appendQuery(endpoint, safeParameters))
                        .method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                byte[] body = objectMapper.writeValueAsBytes(safeParameters);
                if (body.length > MAX_REQUEST_BYTES) throw new BusinessException(400, "工具请求参数超过 256KB 限制");
                builder = HttpRequest.newBuilder(endpoint)
                        .header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
            }
            builder.timeout(Duration.ofSeconds(tool.getTimeoutSeconds())).header("Accept", "application/json");
            applyCredential(builder, tool);
            HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] responseBody;
            try (InputStream stream = response.body()) { responseBody = stream.readNBytes(MAX_RESPONSE_BYTES + 1); }
            if (responseBody.length > MAX_RESPONSE_BYTES) throw new BusinessException(502, "工具响应超过 2MB 限制");
            return new ToolResponse(response.statusCode(), new String(responseBody, StandardCharsets.UTF_8));
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "工具请求被中断", e);
        } catch (Exception e) {
            throw new BusinessException(502, "工具请求失败: " + safeMessage(e), e);
        }
    }

    private void applyCredential(HttpRequest.Builder builder, AgentTool tool) {
        if ("none".equals(tool.getAuthType())) return;
        String credential = credentialResolver.resolve(tool.getCredentialRef());
        if (credential == null || credential.isBlank()) throw new BusinessException(500, "工具配置了认证但没有可用凭据");
        if ("bearer".equals(tool.getAuthType())) builder.header("Authorization", "Bearer " + credential);
        else builder.header(tool.getAuthHeader(), credential);
    }

    private URI appendQuery(URI uri, Map<String, Object> parameters) {
        if (parameters.isEmpty()) return uri;
        StringBuilder value = new StringBuilder(uri.toString());
        value.append(uri.getRawQuery() == null ? '?' : '&');
        boolean first = true;
        for (Map.Entry<String, Object> entry : new TreeMap<>(parameters).entrySet()) {
            if (!entry.getKey().matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
                throw new BusinessException(400, "GET 参数名格式不正确: " + entry.getKey());
            }
            if (!first) value.append('&');
            first = false;
            value.append(encode(entry.getKey())).append('=').append(encode(String.valueOf(entry.getValue())));
        }
        return URI.create(value.toString());
    }

    private void validateNetworkTarget(URI uri, boolean allowPrivate) {
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()) {
                    throw new BusinessException(400, "工具禁止访问回环、链路本地或组播地址");
                }
                byte[] raw = address.getAddress();
                boolean uniqueLocalV6 = raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
                if (!allowPrivate && (address.isSiteLocalAddress() || uniqueLocalV6)) {
                    throw new BusinessException(400, "工具默认禁止访问内网地址；确有需要请显式启用");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "无法解析工具 endpoint 主机");
        }
    }

    private URI parseUri(String value) {
        try { return URI.create(value == null ? "" : value.trim()); }
        catch (Exception e) { throw new BusinessException(400, "工具 endpoint 地址格式不正确"); }
    }

    private String normalizedMethod(String value) {
        String method = normalize(value, "POST").toUpperCase(Locale.ROOT);
        if (!List.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method)) {
            throw new BusinessException(400, "工具请求方法仅支持 GET、POST、PUT、PATCH、DELETE");
        }
        return method;
    }

    private void validateHeaderName(String value) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9-]{0,63}")) {
            throw new BusinessException(400, "认证请求头格式不正确");
        }
        if (List.of("host", "content-length").contains(value.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(400, "禁止使用该认证请求头");
        }
    }

    private void validateCredentialRef(String value) {
        if (value == null || value.isBlank()) return;
        if (!value.matches("env:[A-Za-z_][A-Za-z0-9_]{0,127}")) {
            throw new BusinessException(400, "凭据仅支持 env:VARIABLE_NAME 引用");
        }
    }

    private String safeMessage(Exception e) {
        String value = e.getMessage();
        return value == null || value.isBlank() ? e.getClass().getSimpleName() : value.substring(0, Math.min(300, value.length()));
    }

    private String normalize(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
