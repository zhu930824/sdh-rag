package cn.sdh.backend.knowledge.connector;

import cn.sdh.backend.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RestJsonKnowledgeSourceConnector implements KnowledgeSourceConnector {
    private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public RestJsonKnowledgeSourceConnector(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    RestJsonKnowledgeSourceConnector(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override public String type() { return "rest_json"; }
    @Override public Mode mode() { return Mode.PULL; }

    @Override
    public void validate(Map<String, Object> configuration) {
        Map<String, Object> config = configuration == null ? Map.of() : configuration;
        String endpoint = string(config, "endpoint", null);
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > 2000) {
            throw new BusinessException(400, "REST 数据源必须配置 endpoint");
        }
        URI uri = parseUri(endpoint);
        boolean allowHttp = bool(config, "allowInsecureHttp", false);
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !(allowHttp && "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new BusinessException(400, "REST 数据源必须使用 HTTPS；测试 HTTP 需显式启用 allowInsecureHttp");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new BusinessException(400, "endpoint 地址格式不正确");
        }
        String authType = string(config, "authType", "none").toLowerCase(Locale.ROOT);
        if (!List.of("none", "bearer", "header").contains(authType)) throw new BusinessException(400, "authType 仅支持 none、bearer 或 header");
        if ("header".equals(authType)) validateHeaderName(string(config, "authHeader", null));
        integer(config, "timeoutSeconds", 30, 5, 120);
        integer(config, "pageSize", 100, 1, 500);
        integer(config, "maxPagesPerRun", 20, 1, 100);
        integer(config, "maxRunSeconds", 600, 30, 900);
    }

    @Override
    public FetchResult fetch(FetchRequest request) {
        Map<String, Object> config = request.configuration() == null ? Map.of() : request.configuration();
        validate(config);
        URI endpoint = parseUri(string(config, "endpoint", null));
        validateNetworkTarget(endpoint, bool(config, "allowPrivateNetwork", false));
        String cursorParam = parameterName(config, "cursorParam", "cursor");
        String limitParam = parameterName(config, "limitParam", "limit");
        URI requestUri = appendQuery(endpoint, cursorParam, request.cursor(), limitParam, String.valueOf(request.limit()));
        HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri)
                .timeout(Duration.ofSeconds(integer(config, "timeoutSeconds", 30, 5, 120)))
                .header("Accept", "application/json")
                .GET();
        applyCredential(builder, config, request.credential());
        try {
            HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (InputStream ignored = response.body()) { /* close without retaining remote error content */ }
                throw new BusinessException(502, "REST 数据源返回非成功状态: " + response.statusCode());
            }
            byte[] body;
            try (InputStream stream = response.body()) { body = stream.readNBytes(MAX_RESPONSE_BYTES + 1); }
            if (body.length > MAX_RESPONSE_BYTES) throw new BusinessException(502, "REST 数据源响应超过 5MB 限制");
            return parseResponse(body, request.limit());
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "REST 数据源请求被中断", e);
        } catch (Exception e) {
            throw new BusinessException(502, "REST 数据源请求失败: " + e.getMessage(), e);
        }
    }

    FetchResult parseResponse(byte[] body, int limit) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode itemsNode = root == null ? null : root.get("items");
        if (itemsNode == null || !itemsNode.isArray()) throw new BusinessException(502, "REST 响应必须包含 items 数组");
        if (itemsNode.size() > limit) throw new BusinessException(502, "REST 响应条目数超过请求的 pageSize");
        List<SourceItem> items = new ArrayList<>(itemsNode.size());
        for (JsonNode node : itemsNode) {
            Map<String, Object> metadata = node.has("metadata") && node.get("metadata").isObject()
                    ? objectMapper.convertValue(node.get("metadata"), new TypeReference<>() {}) : Map.of();
            items.add(new SourceItem(text(node, "externalId"), text(node, "title"), text(node, "content"),
                    text(node, "sourceUrl"), instant(node, "sourceUpdatedAt"), metadata,
                    node.path("deleted").asBoolean(false)));
        }
        return new FetchResult(items, text(root, "nextCursor"), root.path("hasMore").asBoolean(false));
    }

    private void applyCredential(HttpRequest.Builder builder, Map<String, Object> config, String credential) {
        String authType = string(config, "authType", "none").toLowerCase(Locale.ROOT);
        if ("none".equals(authType)) return;
        if (credential == null || credential.isBlank()) throw new BusinessException(500, "数据源配置了认证但没有可用凭据");
        if ("bearer".equals(authType)) builder.header("Authorization", "Bearer " + credential);
        else builder.header(string(config, "authHeader", null), credential);
    }

    private void validateNetworkTarget(URI uri, boolean allowPrivate) {
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()) {
                    throw new BusinessException(400, "endpoint 禁止访问回环、链路本地或组播地址");
                }
                byte[] raw = address.getAddress();
                boolean uniqueLocalV6 = raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
                if (!allowPrivate && (address.isSiteLocalAddress() || uniqueLocalV6)) {
                    throw new BusinessException(400, "endpoint 默认禁止访问内网地址；确有需要请启用 allowPrivateNetwork");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "无法解析 endpoint 主机: " + e.getMessage());
        }
    }

    private URI appendQuery(URI uri, String firstName, String firstValue, String secondName, String secondValue) {
        StringBuilder value = new StringBuilder(uri.toString());
        value.append(uri.getRawQuery() == null ? '?' : '&');
        if (firstValue != null && !firstValue.isBlank()) {
            value.append(encode(firstName)).append('=').append(encode(firstValue)).append('&');
        }
        value.append(encode(secondName)).append('=').append(encode(secondValue));
        return URI.create(value.toString());
    }

    private String parameterName(Map<String, Object> config, String key, String defaultValue) {
        String result = string(config, key, defaultValue);
        if (!result.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) throw new BusinessException(400, key + " 格式不正确");
        return result;
    }

    private void validateHeaderName(String value) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9-]{0,63}")) throw new BusinessException(400, "authHeader 格式不正确");
        if (List.of("host", "content-length").contains(value.toLowerCase(Locale.ROOT))) throw new BusinessException(400, "禁止设置该认证请求头");
    }

    private URI parseUri(String value) {
        try { return URI.create(value); }
        catch (Exception e) { throw new BusinessException(400, "endpoint 地址格式不正确"); }
    }

    private int integer(Map<String, Object> config, String key, int defaultValue, int min, int max) {
        Object value = config.get(key);
        int result;
        try { result = value == null ? defaultValue : Integer.parseInt(String.valueOf(value)); }
        catch (Exception e) { throw new BusinessException(400, key + " 必须是整数"); }
        if (result < min || result > max) throw new BusinessException(400, key + " 必须在 " + min + "-" + max + " 之间");
        return result;
    }

    private boolean bool(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private String string(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value == null ? defaultValue : String.valueOf(value).trim();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); }
        catch (Exception e) { throw new BusinessException(502, field + " 必须是 ISO-8601 UTC 时间"); }
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
