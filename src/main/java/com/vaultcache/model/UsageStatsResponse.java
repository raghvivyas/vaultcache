package com.vaultcache.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UsageStatsResponse {
    private String  subject;
    private long    totalAllowed;
    private long    totalThrottled;
    private double  throttleRatePct;
    private double  avgRedisLatencyMs;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant lastSeenAt;
}
