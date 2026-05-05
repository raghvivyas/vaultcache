package com.vaultcache.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The result of a single rate limit check.
 * Returned by RateLimiterService and also embedded in HTTP response headers.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RateLimitResult {

    /** true = request allowed, false = request throttled */
    private boolean allowed;

    /** The key used for this check (API key / IP / username) */
    private String  key;

    /** Which algorithm evaluated this check */
    private Algorithm algorithm;

    /** Configured limit (requests per window) */
    private int limit;

    /** How many requests remain in the current window */
    private int remaining;

    /** Unix epoch seconds when the window resets (null for token bucket) */
    private Long resetAt;

    /** How long (ms) the Redis Lua script took */
    private long redisLatencyMs;
}
