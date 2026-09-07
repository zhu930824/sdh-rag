package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.KnowledgeSource;
import cn.sdh.backend.entity.KnowledgeSourceApiAudit;
import cn.sdh.backend.entity.KnowledgeSourceApiKey;
import cn.sdh.backend.mapper.KnowledgeSourceApiAuditMapper;
import cn.sdh.backend.mapper.KnowledgeSourceApiKeyMapper;
import cn.sdh.backend.mapper.KnowledgeSourceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSourceApiKeyService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_MARKER = "sdh_";
    private final KnowledgeSourceApiKeyMapper apiKeys;
    private final KnowledgeSourceApiAuditMapper audits;
    private final KnowledgeSourceMapper sources;
    private final KnowledgeAccessService access;

    public List<ApiKeySummary> list(Long knowledgeId, Long sourceId) {
        requireOwnerSource(knowledgeId, sourceId);
        return apiKeys.selectList(new LambdaQueryWrapper<KnowledgeSourceApiKey>()
                        .eq(KnowledgeSourceApiKey::getSourceId, sourceId)
                        .orderByDesc(KnowledgeSourceApiKey::getCreateTime))
                .stream().map(this::summary).toList();
    }

    @Transactional
    public CreatedApiKey create(Long knowledgeId, Long sourceId, ApiKeyCommand command) {
        KnowledgeSource source = requireOwnerSource(knowledgeId, sourceId);
        requirePushSource(source);
        validate(command);
        Long activeCount = apiKeys.selectCount(new LambdaQueryWrapper<KnowledgeSourceApiKey>()
                .eq(KnowledgeSourceApiKey::getSourceId, sourceId)
                .eq(KnowledgeSourceApiKey::getStatus, 1));
        if (activeCount != null && activeCount >= 20) throw new BusinessException(400, "单个数据源最多保留 20 个有效 API Key");
        return createKey(source, command);
    }

    @Transactional
    public CreatedApiKey rotate(Long knowledgeId, Long sourceId, Long keyId) {
        KnowledgeSource source = requireOwnerSource(knowledgeId, sourceId);
        requirePushSource(source);
        KnowledgeSourceApiKey previous = requireKey(sourceId, keyId);
        if (!Integer.valueOf(1).equals(previous.getStatus())) throw new BusinessException(400, "API Key 已吊销，不能再次轮换");
        if (previous.getExpiresAt() != null && !previous.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(400, "API Key 已过期，请新建密钥并设置新的过期时间");
        }
        revokeEntity(previous);
        return createKey(source, new ApiKeyCommand(previous.getName(), previous.getRateLimitPerMinute(), previous.getExpiresAt()));
    }

    public void revoke(Long knowledgeId, Long sourceId, Long keyId) {
        requireOwnerSource(knowledgeId, sourceId);
        KnowledgeSourceApiKey key = requireKey(sourceId, keyId);
        if (!Integer.valueOf(1).equals(key.getStatus())) return;
        revokeEntity(key);
    }

    public List<KnowledgeSourceApiAudit> auditHistory(Long knowledgeId, Long sourceId, int requestedLimit) {
        requireOwnerSource(knowledgeId, sourceId);
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        return audits.selectList(new LambdaQueryWrapper<KnowledgeSourceApiAudit>()
                .eq(KnowledgeSourceApiAudit::getSourceId, sourceId)
                .orderByDesc(KnowledgeSourceApiAudit::getCreateTime)
                .last("LIMIT " + limit));
    }

    public AuthenticatedApiKey authenticate(String rawKey) {
        ParsedKey parsed = parse(rawKey);
        KnowledgeSourceApiKey key = apiKeys.selectOne(new LambdaQueryWrapper<KnowledgeSourceApiKey>()
                .eq(KnowledgeSourceApiKey::getKeyPrefix, parsed.prefix()));
        if (key == null || !Integer.valueOf(1).equals(key.getStatus())) throw new BusinessException(401, "API Key 无效或已吊销");
        if (key.getExpiresAt() != null && !key.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(401, "API Key 已过期");
        }
        byte[] expected = key.getKeyHash().getBytes(StandardCharsets.US_ASCII);
        byte[] actual = sha256(rawKey).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) throw new BusinessException(401, "API Key 无效或已吊销");
        apiKeys.update(null, new UpdateWrapper<KnowledgeSourceApiKey>()
                .eq("id", key.getId())
                .eq("status", 1)
                .set("last_used_at", LocalDateTime.now()));
        return new AuthenticatedApiKey(key.getId(), key.getSourceId(), key.getKnowledgeId(), key.getCreatedBy(),
                key.getName(), key.getRateLimitPerMinute() == null ? 60 : key.getRateLimitPerMinute());
    }

    public void recordAudit(AuthenticatedApiKey key, String requestId, int itemCount, Long taskId,
                            String status, int responseCode, String error, long durationMs, String userAgent) {
        try {
            KnowledgeSourceApiAudit audit = new KnowledgeSourceApiAudit();
            audit.setApiKeyId(key.id()); audit.setSourceId(key.sourceId()); audit.setKnowledgeId(key.knowledgeId());
            audit.setRequestId(requestId); audit.setItemCount(Math.max(0, itemCount)); audit.setTaskId(taskId);
            audit.setResultStatus(status); audit.setResponseCode(responseCode); audit.setErrorMessage(trim(error, 1000));
            audit.setDurationMs(Math.max(0, durationMs)); audit.setUserAgent(trim(userAgent, 255));
            audit.setCreateTime(LocalDateTime.now());
            audits.insert(audit);
        } catch (Exception e) {
            log.error("记录知识源 API 调用审计失败: requestId={}", requestId, e);
        }
    }

    private CreatedApiKey createKey(KnowledgeSource source, ApiKeyCommand command) {
        String prefix = KEY_MARKER + randomPart(9);
        String rawKey = prefix + "." + randomPart(32);
        KnowledgeSourceApiKey key = new KnowledgeSourceApiKey();
        key.setSourceId(source.getId()); key.setKnowledgeId(source.getKnowledgeId()); key.setName(command.name().trim());
        key.setKeyPrefix(prefix); key.setKeyHash(sha256(rawKey)); key.setRateLimitPerMinute(command.rateLimitPerMinute());
        key.setStatus(1); key.setExpiresAt(command.expiresAt()); key.setCreatedBy(UserContext.getCurrentUserId());
        key.setCreateTime(LocalDateTime.now());
        apiKeys.insert(key);
        return new CreatedApiKey(key.getId(), key.getName(), key.getKeyPrefix(), rawKey,
                key.getRateLimitPerMinute(), key.getExpiresAt(), key.getCreateTime());
    }

    private void revokeEntity(KnowledgeSourceApiKey key) {
        key.setStatus(0); key.setRevokeTime(LocalDateTime.now());
        apiKeys.updateById(key);
    }

    private KnowledgeSource requireOwnerSource(Long knowledgeId, Long sourceId) {
        access.requireOwner(knowledgeId, UserContext.getCurrentUserId());
        KnowledgeSource source = sources.selectById(sourceId);
        if (source == null || !knowledgeId.equals(source.getKnowledgeId())) throw new BusinessException(404, "知识源不存在");
        return source;
    }

    private KnowledgeSourceApiKey requireKey(Long sourceId, Long keyId) {
        KnowledgeSourceApiKey key = apiKeys.selectById(keyId);
        if (key == null || !sourceId.equals(key.getSourceId())) throw new BusinessException(404, "API Key 不存在");
        return key;
    }

    private void requirePushSource(KnowledgeSource source) {
        if (!"api_push".equals(source.getSourceType())) throw new BusinessException(400, "只有 API Push 数据源需要接入密钥");
    }

    private void validate(ApiKeyCommand command) {
        if (command == null || command.name() == null || command.name().isBlank()) throw new BusinessException(400, "请输入 API Key 名称");
        if (command.name().trim().length() > 80) throw new BusinessException(400, "API Key 名称不能超过 80 个字符");
        if (command.rateLimitPerMinute() == null || command.rateLimitPerMinute() < 1 || command.rateLimitPerMinute() > 10_000) {
            throw new BusinessException(400, "每分钟限流值必须在 1-10000 之间");
        }
        if (command.expiresAt() != null && !command.expiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(400, "过期时间必须晚于当前时间");
        }
    }

    private ParsedKey parse(String rawKey) {
        if (rawKey == null || rawKey.isBlank() || rawKey.length() > 128) throw new BusinessException(401, "缺少有效的 API Key");
        int separator = rawKey.indexOf('.');
        if (separator < 5 || separator == rawKey.length() - 1) throw new BusinessException(401, "API Key 格式不正确");
        String prefix = rawKey.substring(0, separator);
        if (!prefix.startsWith(KEY_MARKER)) throw new BusinessException(401, "API Key 格式不正确");
        return new ParsedKey(prefix);
    }

    private ApiKeySummary summary(KnowledgeSourceApiKey key) {
        return new ApiKeySummary(key.getId(), key.getName(), key.getKeyPrefix(), key.getRateLimitPerMinute(),
                key.getStatus(), key.getExpiresAt(), key.getLastUsedAt(), key.getCreateTime(), key.getRevokeTime());
    }

    private String randomPart(int bytes) {
        byte[] value = new byte[bytes]; RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256", e);
        }
    }

    private String trim(String value, int max) { return value == null ? null : value.substring(0, Math.min(value.length(), max)); }

    public record ApiKeyCommand(String name, Integer rateLimitPerMinute, LocalDateTime expiresAt) {}
    public record ApiKeySummary(Long id, String name, String keyPrefix, Integer rateLimitPerMinute, Integer status,
                                LocalDateTime expiresAt, LocalDateTime lastUsedAt, LocalDateTime createTime, LocalDateTime revokeTime) {}
    public record CreatedApiKey(Long id, String name, String keyPrefix, String apiKey, Integer rateLimitPerMinute,
                                LocalDateTime expiresAt, LocalDateTime createTime) {}
    public record AuthenticatedApiKey(Long id, Long sourceId, Long knowledgeId, Long createdBy, String name, int rateLimitPerMinute) {}
    private record ParsedKey(String prefix) {}
}
