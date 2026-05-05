package com.vaultcache.entity;

import com.vaultcache.model.Algorithm;
import com.vaultcache.model.LimitKeyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

/**
 * Persisted rate limit configuration for a subject (API key / IP / user).
 * Stored in PostgreSQL so configurations survive restarts and can be
 * updated live without redeployment.
 */
@Entity
@Table(name = "rate_limit_configs",
       indexes = {
           @Index(name = "idx_rlc_subject",  columnList = "subject"),
           @Index(name = "idx_rlc_key_type", columnList = "key_type")
       })
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RateLimitConfigEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The subject being limited: an API key value, IP address, or username */
    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false, length = 20)
    private LimitKeyType keyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm", nullable = false, length = 30)
    private Algorithm algorithm;

    /** Requests per window (or bucket capacity for TOKEN_BUCKET) */
    @Column(name = "rate_limit", nullable = false)
    private int limit;

    /** Window size in seconds */
    @Column(name = "window_seconds", nullable = false)
    private int windowSeconds;

    /** Token refill rate per second (TOKEN_BUCKET only) */
    @Column(name = "refill_rate_per_second")
    private int refillRatePerSecond;

    /** Tier label: FREE / PRO / ENTERPRISE */
    @Column(name = "tier", length = 30)
    private String tier;

    /** If false, all requests are rejected (acts as a blocklist) */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** If true, all requests are rejected regardless of count */
    @Column(name = "blocked", nullable = false)
    private boolean blocked = false;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
