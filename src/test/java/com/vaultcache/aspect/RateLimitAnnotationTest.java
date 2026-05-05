package com.vaultcache.aspect;

import com.vaultcache.annotation.RateLimit;
import com.vaultcache.model.Algorithm;
import com.vaultcache.model.LimitKeyType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the @RateLimit annotation can be read via reflection
 * and that default values are correct.
 * Pure Java test — no Spring context, no mocking needed.
 */
class RateLimitAnnotationTest {

    static class SampleController {
        @RateLimit
        public void defaultMethod() {}

        @RateLimit(limit = 50, windowSeconds = 30,
                   algorithm = Algorithm.FIXED_WINDOW,
                   keyType = LimitKeyType.IP)
        public void customMethod() {}

        @RateLimit(limit = 100, refillRatePerSecond = 5,
                   algorithm = Algorithm.TOKEN_BUCKET,
                   keyPrefix = "bucket-op")
        public void tokenBucketMethod() {}
    }

    @Test
    void defaultAnnotation_hasExpectedDefaults() throws NoSuchMethodException {
        Method m = SampleController.class.getMethod("defaultMethod");
        RateLimit annotation = m.getAnnotation(RateLimit.class);

        assertThat(annotation.limit()).isEqualTo(60);
        assertThat(annotation.windowSeconds()).isEqualTo(60);
        assertThat(annotation.algorithm()).isEqualTo(Algorithm.SLIDING_WINDOW);
        assertThat(annotation.keyType()).isEqualTo(LimitKeyType.API_KEY);
        assertThat(annotation.keyPrefix()).isEmpty();
    }

    @Test
    void customAnnotation_valuesArePreserved() throws NoSuchMethodException {
        Method m = SampleController.class.getMethod("customMethod");
        RateLimit annotation = m.getAnnotation(RateLimit.class);

        assertThat(annotation.limit()).isEqualTo(50);
        assertThat(annotation.windowSeconds()).isEqualTo(30);
        assertThat(annotation.algorithm()).isEqualTo(Algorithm.FIXED_WINDOW);
        assertThat(annotation.keyType()).isEqualTo(LimitKeyType.IP);
    }

    @Test
    void tokenBucketAnnotation_hasPrefix() throws NoSuchMethodException {
        Method m = SampleController.class.getMethod("tokenBucketMethod");
        RateLimit annotation = m.getAnnotation(RateLimit.class);

        assertThat(annotation.algorithm()).isEqualTo(Algorithm.TOKEN_BUCKET);
        assertThat(annotation.refillRatePerSecond()).isEqualTo(5);
        assertThat(annotation.keyPrefix()).isEqualTo("bucket-op");
    }

    @Test
    void annotation_isRetainedAtRuntime() throws NoSuchMethodException {
        Method m = SampleController.class.getMethod("defaultMethod");
        assertThat(m.isAnnotationPresent(RateLimit.class)).isTrue();
    }
}
