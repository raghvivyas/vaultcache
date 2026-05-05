package com.vaultcache.service;

import com.vaultcache.entity.RateLimitConfigEntity;
import com.vaultcache.entity.UsageEventEntity;
import com.vaultcache.exception.ResourceNotFoundException;
import com.vaultcache.model.*;
import com.vaultcache.repository.RateLimitConfigRepository;
import com.vaultcache.repository.UsageEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitConfigService {

    private final RateLimitConfigRepository configRepo;
    private final UsageEventRepository      usageRepo;
    private final StringRedisTemplate       redisTemplate;

    @Transactional
    public RateLimitConfigResponse create(RateLimitConfigRequest req) {
        if (configRepo.existsBySubject(req.getSubject())) {
            throw new IllegalArgumentException(
                    "Config for subject '" + req.getSubject() + "' already exists. Use PUT to update.");
        }
        RateLimitConfigEntity entity = toEntity(req);
        entity = configRepo.save(entity);
        log.info("Created rate limit config: subject={} algorithm={} limit={}/{}s",
                req.getSubject(), req.getAlgorithm(), req.getLimit(), req.getWindowSeconds());
        return toResponse(entity);
    }

    @Transactional
    public RateLimitConfigResponse update(Long id, RateLimitConfigRequest req) {
        RateLimitConfigEntity entity = configRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Config not found: " + id));
        entity.setSubject(req.getSubject());
        entity.setKeyType(req.getKeyType());
        entity.setAlgorithm(req.getAlgorithm());
        entity.setLimit(req.getLimit());
        entity.setWindowSeconds(req.getWindowSeconds());
        entity.setRefillRatePerSecond(req.getRefillRatePerSecond());
        entity.setTier(req.getTier());
        entity = configRepo.save(entity);

        // Invalidate Redis keys for this subject so new limits apply immediately
        invalidateRedisKeys(req.getSubject());

        log.info("Updated rate limit config id={} subject={}", id, req.getSubject());
        return toResponse(entity);
    }

    @Transactional
    public void block(Long id, boolean blocked) {
        RateLimitConfigEntity entity = configRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Config not found: " + id));
        entity.setBlocked(blocked);
        configRepo.save(entity);
        log.info("{} subject={}", blocked ? "Blocked" : "Unblocked", entity.getSubject());
    }

    @Transactional
    public void delete(Long id) {
        RateLimitConfigEntity entity = configRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Config not found: " + id));
        invalidateRedisKeys(entity.getSubject());
        configRepo.deleteById(id);
        log.info("Deleted rate limit config id={} subject={}", id, entity.getSubject());
    }

    @Transactional(readOnly = true)
    public List<RateLimitConfigResponse> listAll() {
        return configRepo.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RateLimitConfigResponse getById(Long id) {
        return configRepo.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Config not found: " + id));
    }

    @Transactional(readOnly = true)
    public UsageStatsResponse getStats(String subject) {
        long allowed    = usageRepo.countBySubjectAndAllowedTrue(subject);
        long throttled  = usageRepo.countBySubjectAndAllowedFalse(subject);
        long total      = allowed + throttled;
        BigDecimal avgLatency = usageRepo.avgLatencyBySubject(subject);
        Instant lastSeen = usageRepo.lastSeenAt(subject);

        double throttleRate = total == 0 ? 0.0
                : BigDecimal.valueOf(throttled * 100.0 / total)
                            .setScale(1, RoundingMode.HALF_UP).doubleValue();

        return UsageStatsResponse.builder()
                .subject(subject)
                .totalAllowed(allowed)
                .totalThrottled(throttled)
                .throttleRatePct(throttleRate)
                .avgRedisLatencyMs(avgLatency != null
                        ? avgLatency.setScale(2, RoundingMode.HALF_UP).doubleValue() : 0.0)
                .lastSeenAt(lastSeen)
                .build();
    }

    @Transactional(readOnly = true)
    public List<Object[]> topThrottled(int limit) {
        return usageRepo.findTopThrottledSubjects(PageRequest.of(0, limit));
    }

    /** Deletes all Redis keys matching the pattern rl:*:{subject} */
    private void invalidateRedisKeys(String subject) {
        try {
            Set<String> keys = redisTemplate.keys("rl:*:" + subject);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Invalidated {} Redis keys for subject={}", keys.size(), subject);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate Redis keys for {}: {}", subject, e.getMessage());
        }
    }

    private RateLimitConfigEntity toEntity(RateLimitConfigRequest req) {
        return RateLimitConfigEntity.builder()
                .subject(req.getSubject())
                .keyType(req.getKeyType())
                .algorithm(req.getAlgorithm())
                .limit(req.getLimit())
                .windowSeconds(req.getWindowSeconds())
                .refillRatePerSecond(req.getRefillRatePerSecond() > 0 ? req.getRefillRatePerSecond() : 10)
                .tier(req.getTier())
                .active(true)
                .blocked(false)
                .build();
    }

    private RateLimitConfigResponse toResponse(RateLimitConfigEntity e) {
        return RateLimitConfigResponse.builder()
                .id(e.getId())
                .subject(e.getSubject())
                .keyType(e.getKeyType())
                .algorithm(e.getAlgorithm())
                .limit(e.getLimit())
                .windowSeconds(e.getWindowSeconds())
                .refillRatePerSecond(e.getRefillRatePerSecond())
                .tier(e.getTier())
                .active(e.isActive())
                .blocked(e.isBlocked())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
