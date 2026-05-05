package com.vaultcache.controller;

import com.vaultcache.annotation.RateLimit;
import com.vaultcache.model.Algorithm;
import com.vaultcache.model.ApiResponse;
import com.vaultcache.model.LimitKeyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demo endpoints — each one showcases a different rate limiting algorithm.
 * These are PUBLIC (no JWT required) so they are easy to test with curl.
 *
 * These endpoints are the star of the show in demos and interview discussions.
 * Run:  for i in $(seq 1 15); do curl -s -H "X-API-Key: demo-key" http://localhost:8080/api/demo/sliding | jq .status; done
 * You'll see "allowed" for the first 10, then "throttled" for the rest.
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    /**
     * Sliding Window — most accurate algorithm.
     * Limit: 10 requests per 60 seconds per API key.
     */
    @RateLimit(limit = 10, windowSeconds = 60,
               algorithm = Algorithm.SLIDING_WINDOW,
               keyType = LimitKeyType.API_KEY,
               keyPrefix = "demo-sliding")
    @GetMapping("/sliding")
    public ResponseEntity<Map<String, Object>> slidingWindow(
            @RequestHeader(value = "X-API-Key", defaultValue = "anonymous") String apiKey) {
        return ResponseEntity.ok(buildResponse("sliding-window", apiKey));
    }

    /**
     * Fixed Window — simplest algorithm, cheapest on Redis.
     * Limit: 5 requests per 30 seconds per API key.
     */
    @RateLimit(limit = 5, windowSeconds = 30,
               algorithm = Algorithm.FIXED_WINDOW,
               keyType = LimitKeyType.API_KEY,
               keyPrefix = "demo-fixed")
    @GetMapping("/fixed")
    public ResponseEntity<Map<String, Object>> fixedWindow(
            @RequestHeader(value = "X-API-Key", defaultValue = "anonymous") String apiKey) {
        return ResponseEntity.ok(buildResponse("fixed-window", apiKey));
    }

    /**
     * Token Bucket — handles burst traffic gracefully.
     * Capacity: 20 tokens. Refill rate: 2 tokens/second.
     * Send 25 requests rapidly — first 20 pass, then it throttles.
     * Wait 5 seconds — 10 tokens refill, next 10 requests pass.
     */
    @RateLimit(limit = 20, refillRatePerSecond = 2,
               algorithm = Algorithm.TOKEN_BUCKET,
               keyType = LimitKeyType.API_KEY,
               keyPrefix = "demo-bucket")
    @GetMapping("/token-bucket")
    public ResponseEntity<Map<String, Object>> tokenBucket(
            @RequestHeader(value = "X-API-Key", defaultValue = "anonymous") String apiKey) {
        return ResponseEntity.ok(buildResponse("token-bucket", apiKey));
    }

    /**
     * IP-based rate limiting — no API key required.
     * Limit: 20 requests per 60 seconds per IP address.
     */
    @RateLimit(limit = 20, windowSeconds = 60,
               algorithm = Algorithm.SLIDING_WINDOW,
               keyType = LimitKeyType.IP,
               keyPrefix = "demo-ip")
    @GetMapping("/by-ip")
    public ResponseEntity<Map<String, Object>> byIp() {
        return ResponseEntity.ok(buildResponse("ip-based", "your-ip"));
    }

    private Map<String, Object> buildResponse(String algorithm, String subject) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status",    "allowed");
        m.put("algorithm", algorithm);
        m.put("subject",   subject);
        m.put("timestamp", Instant.now().toString());
        return m;
    }
}
