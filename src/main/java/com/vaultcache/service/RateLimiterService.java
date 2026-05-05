package com.vaultcache.service;

import com.vaultcache.model.Algorithm;
import com.vaultcache.model.RateLimitResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Core rate limiting service — executes atomic Lua scripts in Redis.
 *
 * All three algorithms use Lua scripts loaded into Redis via EVALSHA.
 * A Lua script runs atomically on the Redis server — no race conditions,
 * no need for distributed locks, no WATCH/MULTI/EXEC complexity.
 *
 * Why Lua instead of Redis commands directly?
 *   A naive Java implementation would do:
 *     1. GET current count
 *     2. if count < limit: INCR; SET TTL
 *   Between steps 1 and 2, another thread can increment the counter —
 *   a classic TOCTOU race. Two threads both see count=99 (limit=100),
 *   both increment → actual count becomes 101. Lua runs atomically, so
 *   this race is impossible.
 *
 * Redis single-threaded execution model: Redis processes commands one at a
 * time. A Lua script is a single command from Redis's perspective, so the
 * entire script executes without any interleaving from other clients.
 */
@Slf4j
@Service
public class RateLimiterService {

    private final StringRedisTemplate              redisTemplate;
    private final DefaultRedisScript<Long>         fixedWindowScript;
    private final DefaultRedisScript<Long>         slidingWindowScript;
    private final DefaultRedisScript<Long>         tokenBucketScript;
    private final Counter                          allowedCounter;
    private final Counter                          throttledCounter;
    private final Timer                            redisTimer;

    public RateLimiterService(
            StringRedisTemplate redisTemplate,
            @Qualifier("fixedWindowScript")   DefaultRedisScript<Long> fixedWindowScript,
            @Qualifier("slidingWindowScript") DefaultRedisScript<Long> slidingWindowScript,
            @Qualifier("tokenBucketScript")   DefaultRedisScript<Long> tokenBucketScript,
            Counter allowedRequestsCounter,
            Counter throttledRequestsCounter,
            Timer   redisLatencyTimer) {
        this.redisTemplate      = redisTemplate;
        this.fixedWindowScript  = fixedWindowScript;
        this.slidingWindowScript = slidingWindowScript;
        this.tokenBucketScript  = tokenBucketScript;
        this.allowedCounter     = allowedRequestsCounter;
        this.throttledCounter   = throttledRequestsCounter;
        this.redisTimer         = redisTimer;
    }

    /**
     * Checks if a request should be allowed or throttled.
     *
     * @param key           Redis key prefix (e.g. "rl:login:192.168.1.1")
     * @param algorithm     Which algorithm to use
     * @param limit         Max requests per window (or bucket capacity)
     * @param windowSeconds Window size in seconds
     * @param refillRate    Tokens per second (TOKEN_BUCKET only)
     * @return              RateLimitResult with allowed/throttled decision
     */
    public RateLimitResult check(String key, Algorithm algorithm,
                                  int limit, int windowSeconds, int refillRate) {
        long startMs = System.currentTimeMillis();
        boolean allowed;

        try {
            Long result = redisTimer.record(() -> executeScript(key, algorithm, limit, windowSeconds, refillRate));
            allowed = (result != null && result == 1L);
        } catch (Exception e) {
            // Redis failure → fail open (allow the request) to avoid cascading outage
            log.error("Redis rate limit check failed for key={}: {}. Failing open.", key, e.getMessage());
            allowed = true;
        }

        long latencyMs = System.currentTimeMillis() - startMs;

        if (allowed) {
            allowedCounter.increment();
        } else {
            throttledCounter.increment();
        }

        log.debug("RateLimit key={} algorithm={} allowed={} latency={}ms",
                key, algorithm, allowed, latencyMs);

        return RateLimitResult.builder()
                .allowed(allowed)
                .key(key)
                .algorithm(algorithm)
                .limit(limit)
                .remaining(allowed ? -1 : 0)   // -1 = not computed (would need extra Redis call)
                .resetAt(Instant.now().getEpochSecond() + windowSeconds)
                .redisLatencyMs(latencyMs)
                .build();
    }

    // ── Private dispatch ────────────────────────────────────────────────────

    private Long executeScript(String key, Algorithm algorithm,
                                int limit, int windowSeconds, int refillRate) {
        List<String> keys = Collections.singletonList(key);
        switch (algorithm) {
            case FIXED_WINDOW:
                return redisTemplate.execute(fixedWindowScript, keys,
                        String.valueOf(windowSeconds),
                        String.valueOf(limit));

            case SLIDING_WINDOW:
                long nowMs        = System.currentTimeMillis();
                long windowMs     = (long) windowSeconds * 1_000L;
                return redisTemplate.execute(slidingWindowScript, keys,
                        String.valueOf(nowMs),
                        String.valueOf(windowMs),
                        String.valueOf(limit));

            case TOKEN_BUCKET:
                long nowSec = System.currentTimeMillis() / 1_000L;
                return redisTemplate.execute(tokenBucketScript, keys,
                        String.valueOf(nowSec),
                        String.valueOf(limit),
                        String.valueOf(refillRate),
                        "1");

            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }
}
