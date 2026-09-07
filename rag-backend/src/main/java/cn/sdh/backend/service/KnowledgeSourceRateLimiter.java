package cn.sdh.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSourceRateLimiter {
    private final StringRedisTemplate redisTemplate;
    private final Map<String, LocalCounter> fallbackCounters = new ConcurrentHashMap<>();

    public Decision acquire(Long apiKeyId, int configuredLimit) {
        int limit = Math.max(1, configuredLimit);
        long minute = Instant.now().getEpochSecond() / 60;
        String key = "knowledge-source:rate:" + apiKeyId + ":" + minute;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) redisTemplate.expire(key, Duration.ofMinutes(2));
            long used = count == null ? limit + 1L : count;
            return new Decision(used <= limit, limit, Math.max(0, limit - used));
        } catch (Exception e) {
            log.warn("知识源 API 限流 Redis 不可用，降级为单节点计数: {}", e.getMessage());
            return acquireLocally(key, limit, minute);
        }
    }

    private Decision acquireLocally(String key, int limit, long minute) {
        if (fallbackCounters.size() > 10_000) {
            fallbackCounters.entrySet().removeIf(entry -> entry.getValue().minute < minute - 1);
        }
        LocalCounter counter = fallbackCounters.computeIfAbsent(key, ignored -> new LocalCounter(minute));
        int used = counter.count.incrementAndGet();
        return new Decision(used <= limit, limit, Math.max(0, limit - used));
    }

    public record Decision(boolean allowed, int limit, long remaining) {}

    private static final class LocalCounter {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();
        private LocalCounter(long minute) { this.minute = minute; }
    }
}
