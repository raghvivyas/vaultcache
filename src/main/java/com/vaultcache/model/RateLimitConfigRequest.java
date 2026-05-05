package com.vaultcache.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.*;

/**
 * Request body for creating or updating a rate limit configuration.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RateLimitConfigRequest {

    /** The subject being limited: an API key value, IP, or username pattern */
    @NotBlank
    private String subject;

    @NotNull
    private LimitKeyType keyType;

    @NotNull
    private Algorithm algorithm;

    /** Max requests per window (FIXED_WINDOW / SLIDING_WINDOW) or bucket capacity (TOKEN_BUCKET) */
    @Min(1) @Max(100_000)
    private int limit;

    /** Window size in seconds (for FIXED_WINDOW and SLIDING_WINDOW) */
    @Min(1) @Max(86400)
    private int windowSeconds;

    /**
     * Token refill rate per second (only used for TOKEN_BUCKET).
     * e.g. limit=100, refillRatePerSecond=10 means bucket refills 10 tokens/s up to 100.
     */
    @Min(1)
    private int refillRatePerSecond;

    /** Tier label for display purposes: FREE / PRO / ENTERPRISE */
    private String tier;
}
