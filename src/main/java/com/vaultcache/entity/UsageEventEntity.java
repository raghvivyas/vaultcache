package com.vaultcache.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

/**
 * A single rate limit evaluation event.
 * Stored for analytics: throttle rates, Redis P99 latency, top consumers.
 *
 * Design note: high-frequency events — we insert one row per API call.
 * For very high traffic (> 10k req/s), these should be batched or written
 * to a time-series store. For portfolio purposes this simple append works well.
 */
@Entity
@Table(name = "usage_events",
       indexes = {
           @Index(name = "idx_ue_subject_time", columnList = "subject,occurred_at DESC"),
           @Index(name = "idx_ue_occurred_at",  columnList = "occurred_at DESC")
       })
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UsageEventEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject",       nullable = false, length = 255)
    private String  subject;

    @Column(name = "allowed",       nullable = false)
    private boolean allowed;

    @Column(name = "algorithm",     length = 30)
    private String  algorithm;

    @Column(name = "redis_latency_ms")
    private long    redisLatencyMs;

    @Column(name = "endpoint",      length = 255)
    private String  endpoint;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @PrePersist
    protected void onCreate() { occurredAt = Instant.now(); }
}
