package com.vaultcache.annotation;

import com.vaultcache.model.Algorithm;
import com.vaultcache.model.LimitKeyType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Drop this annotation on any Spring MVC endpoint to apply rate limiting.
 *
 * Usage examples:
 *
 *   // 100 requests per minute per API key, sliding window
 *   @RateLimit(limit = 100, windowSeconds = 60, algorithm = Algorithm.SLIDING_WINDOW)
 *   @GetMapping("/api/data")
 *   public ResponseEntity<...> getData() { ... }
 *
 *   // 10 requests per minute per IP, fixed window (for login endpoints)
 *   @RateLimit(limit = 10, windowSeconds = 60, keyType = LimitKeyType.IP,
 *              algorithm = Algorithm.FIXED_WINDOW)
 *   @PostMapping("/api/auth/login")
 *   public ResponseEntity<...> login() { ... }
 *
 *   // Token bucket: 50 capacity, refills 5 tokens/second
 *   @RateLimit(limit = 50, refillRatePerSecond = 5, algorithm = Algorithm.TOKEN_BUCKET)
 *   @PostMapping("/api/expensive-operation")
 *   public ResponseEntity<...> expensiveOp() { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Maximum requests allowed per window (or bucket capacity for TOKEN_BUCKET) */
    int limit() default 60;

    /** Window size in seconds. Used for FIXED_WINDOW and SLIDING_WINDOW. */
    int windowSeconds() default 60;

    /** Which identifier to rate limit by */
    LimitKeyType keyType() default LimitKeyType.API_KEY;

    /** Which rate limiting algorithm to use */
    Algorithm algorithm() default Algorithm.SLIDING_WINDOW;

    /**
     * Token refill rate per second (TOKEN_BUCKET only).
     * Ignored for FIXED_WINDOW and SLIDING_WINDOW.
     */
    int refillRatePerSecond() default 10;

    /**
     * Optional prefix for the Redis key.
     * Useful to namespace limits: e.g. "login" creates key "rl:login:{identifier}"
     */
    String keyPrefix() default "";
}
