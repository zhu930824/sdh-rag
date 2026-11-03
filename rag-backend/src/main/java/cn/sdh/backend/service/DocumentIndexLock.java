package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentIndexLock {
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    @Value("${knowledge.documents.index-lock-minutes:120}")
    private long lockMinutes;

    public LockHandle acquire(Long relationId) {
        String key = "knowledge-document:index-lock:" + relationId;
        String owner = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    key, owner, Duration.ofMinutes(Math.max(10, lockMinutes)));
            return Boolean.TRUE.equals(acquired) ? new LockHandle(key, owner) : null;
        } catch (Exception e) {
            throw new BusinessException(503, "文档索引锁服务暂不可用，为避免并发覆盖已停止本次处理", e);
        }
    }

    public void release(LockHandle handle) {
        if (handle == null) return;
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(handle.key()), handle.owner());
        } catch (Exception ignored) {
            // TTL guarantees eventual release.
        }
    }

    public record LockHandle(String key, String owner) {}
}
