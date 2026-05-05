package com.vaultcache.controller;

import com.vaultcache.annotation.RateLimit;
import com.vaultcache.model.Algorithm;
import com.vaultcache.model.LimitKeyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for DemoController.
 * Tests the response structure and verifies @RateLimit annotations are configured correctly.
 * No Spring context, no Redis, no mocking of the aspect (aspect is infrastructure concern).
 */
class DemoControllerTest {

    @InjectMocks
    private DemoController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void slidingWindow_responseContainsExpectedFields() {
        ResponseEntity<Map<String, Object>> response =
                controller.slidingWindow("test-api-key");

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("allowed");
        assertThat(response.getBody().get("algorithm")).isEqualTo("sliding-window");
        assertThat(response.getBody().get("timestamp")).isNotNull();
    }

    @Test
    void fixedWindow_responseContainsExpectedFields() {
        ResponseEntity<Map<String, Object>> response =
                controller.fixedWindow("test-api-key");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("algorithm")).isEqualTo("fixed-window");
    }

    @Test
    void tokenBucket_responseContainsExpectedFields() {
        ResponseEntity<Map<String, Object>> response =
                controller.tokenBucket("test-api-key");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("algorithm")).isEqualTo("token-bucket");
    }

    @Test
    void slidingWindow_annotationHasCorrectConfig() throws NoSuchMethodException {
        Method m = DemoController.class.getMethod("slidingWindow", String.class);
        RateLimit annotation = m.getAnnotation(RateLimit.class);

        assertThat(annotation.limit()).isEqualTo(10);
        assertThat(annotation.windowSeconds()).isEqualTo(60);
        assertThat(annotation.algorithm()).isEqualTo(Algorithm.SLIDING_WINDOW);
        assertThat(annotation.keyType()).isEqualTo(LimitKeyType.API_KEY);
    }

    @Test
    void tokenBucket_annotationHasCorrectConfig() throws NoSuchMethodException {
        Method m = DemoController.class.getMethod("tokenBucket", String.class);
        RateLimit annotation = m.getAnnotation(RateLimit.class);

        assertThat(annotation.algorithm()).isEqualTo(Algorithm.TOKEN_BUCKET);
        assertThat(annotation.limit()).isEqualTo(20);
        assertThat(annotation.refillRatePerSecond()).isEqualTo(2);
    }
}
