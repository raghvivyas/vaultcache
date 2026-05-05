package com.vaultcache.controller;

import com.vaultcache.model.ApiResponse;
import com.vaultcache.repository.UsageEventRepository;
import com.vaultcache.service.RateLimitConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final RateLimitConfigService  configService;
    private final UsageEventRepository    usageRepo;
    private final StringRedisTemplate     redisTemplate;

    /** GET /api/admin/dashboard — system-wide stats */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        long totalAllowed   = usageRepo.countBySubjectAndAllowedTrue("*");
        long totalThrottled = usageRepo.countBySubjectAndAllowedFalse("*");
        long totalEvents    = usageRepo.count();
        Instant lastHour    = Instant.now().minus(1, ChronoUnit.HOURS);
        Number avgLatency   = usageRepo.avgLatencySince(lastHour);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalEvents",       totalEvents);
        m.put("activeConfigs",     configService.listAll().stream().filter(c -> c.isActive()).count());
        m.put("avgRedisLatencyMs", avgLatency != null ? avgLatency : 0);
        m.put("timestamp",         Instant.now().toString());
        return ResponseEntity.ok(m);
    }

    /** GET /api/admin/top-throttled?limit=10 — top throttled subjects */
    @GetMapping("/top-throttled")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> topThrottled(
            @RequestParam(defaultValue = "10") int limit) {
        List<Object[]> raw = configService.topThrottled(Math.min(limit, 50));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("subject", row[0]);
            entry.put("throttledCount", row[1]);
            result.add(entry);
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** GET /api/admin/redis-info — live Redis key counts */
    @GetMapping("/redis-info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> redisInfo() {
        Set<String> keys = redisTemplate.keys("rl:*");
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("activeLimitKeys", keys != null ? keys.size() : 0);
        info.put("timestamp",       Instant.now().toString());
        return ResponseEntity.ok(ApiResponse.ok(info));
    }
}
