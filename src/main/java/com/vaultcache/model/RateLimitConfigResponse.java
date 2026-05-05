package com.vaultcache.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RateLimitConfigResponse {
    private Long      id;
    private String    subject;
    private LimitKeyType keyType;
    private Algorithm algorithm;
    private int       limit;
    private int       windowSeconds;
    private int       refillRatePerSecond;
    private String    tier;
    private boolean   active;
    private boolean   blocked;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant   createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant   updatedAt;
}
