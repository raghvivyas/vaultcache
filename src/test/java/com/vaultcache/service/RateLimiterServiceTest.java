package com.vaultcache.service;

import com.vaultcache.model.Algorithm;
import com.vaultcache.model.RateLimitResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RateLimiterService.
 * Mocks Redis so tests are zero-infrastructure — no running Redis needed.
 */
class RateLimiterServiceTest {

    @Mock private StringRedisTemplate              redisTemplate;
    @Mock private DefaultRedisScript<Long>         fixedWindowScript;
    @Mock private DefaultRedisScript<Long>         slidingWindowScript;
    @Mock private DefaultRedisScript<Long>         tokenBucketScript;
    @Mock private Counter                          allowedCounter;
    @Mock private Counter                          throttledCounter;
    @Mock private Timer                            redisTimer;

    private RateLimiterService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RateLimiterService(
                redisTemplate, fixedWindowScript, slidingWindowScript, tokenBucketScript,
                allowedCounter, throttledCounter, redisTimer);
    }

    @Test
    void check_fixedWindow_redisReturns1_isAllowed() {
        when(redisTimer.record(any(Supplier.class))).thenReturn(1L);

        RateLimitResult result = service.check("rl:test:key1",
                Algorithm.FIXED_WINDOW, 100, 60, 10);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getAlgorithm()).isEqualTo(Algorithm.FIXED_WINDOW);
        assertThat(result.getLimit()).isEqualTo(100);
    }

    @Test
    void check_slidingWindow_redisReturns0_isThrottled() {
        when(redisTimer.record(any(Supplier.class))).thenReturn(0L);

        RateLimitResult result = service.check("rl:test:key2",
                Algorithm.SLIDING_WINDOW, 10, 60, 1);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getAlgorithm()).isEqualTo(Algorithm.SLIDING_WINDOW);
    }

    @Test
    void check_tokenBucket_redisReturns1_isAllowed() {
        when(redisTimer.record(any(Supplier.class))).thenReturn(1L);

        RateLimitResult result = service.check("rl:test:bucket",
                Algorithm.TOKEN_BUCKET, 50, 60, 5);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getAlgorithm()).isEqualTo(Algorithm.TOKEN_BUCKET);
    }

    @Test
    void check_redisFailure_failsOpen() {
        // Redis throws → should fail open (allow the request)
        when(redisTimer.record(any(Supplier.class)))
                .thenThrow(new RuntimeException("Redis connection refused"));

        RateLimitResult result = service.check("rl:test:failopen",
                Algorithm.FIXED_WINDOW, 100, 60, 10);

        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    void check_result_containsExpectedFields() {
        when(redisTimer.record(any(Supplier.class))).thenReturn(1L);

        RateLimitResult result = service.check("rl:myprefix:user123",
                Algorithm.SLIDING_WINDOW, 200, 120, 10);

        assertThat(result.getKey()).isEqualTo("rl:myprefix:user123");
        assertThat(result.getLimit()).isEqualTo(200);
        assertThat(result.getResetAt()).isNotNull();
        assertThat(result.getRedisLatencyMs()).isGreaterThanOrEqualTo(0L);
    }
}
