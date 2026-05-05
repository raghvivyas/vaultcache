package com.vaultcache.aspect;

import com.vaultcache.annotation.RateLimit;
import com.vaultcache.entity.RateLimitConfigEntity;
import com.vaultcache.entity.UsageEventEntity;
import com.vaultcache.model.Algorithm;
import com.vaultcache.model.LimitKeyType;
import com.vaultcache.model.RateLimitResult;
import com.vaultcache.repository.RateLimitConfigRepository;
import com.vaultcache.repository.UsageEventRepository;
import com.vaultcache.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

/**
 * Spring AOP aspect that intercepts every method annotated with @RateLimit.
 *
 * The aspect:
 *   1. Resolves the rate limit key (API key header, IP, or authenticated user)
 *   2. Looks up any dynamic config override from PostgreSQL
 *      (if no DB config exists, uses the annotation values as defaults)
 *   3. Checks the blocklist (blocked=true → immediate 403)
 *   4. Calls RateLimiterService with the appropriate Lua script
 *   5. If throttled → returns HTTP 429 with Retry-After headers, WITHOUT
 *      executing the underlying method
 *   6. If allowed → proceeds normally, adds rate limit headers to response
 *   7. Persists a UsageEventEntity for analytics
 *
 * The @Around advice wraps the entire method call. This means the underlying
 * endpoint code NEVER executes if the request is throttled — zero wasted work.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String HEADER_LIMIT        = "X-RateLimit-Limit";
    private static final String HEADER_REMAINING    = "X-RateLimit-Remaining";
    private static final String HEADER_RESET        = "X-RateLimit-Reset";
    private static final String HEADER_RETRY_AFTER  = "Retry-After";

    private final RateLimiterService          rateLimiter;
    private final RateLimitConfigRepository   configRepo;
    private final UsageEventRepository        usageRepo;

    @Around("@annotation(com.vaultcache.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method        method    = ((MethodSignature) pjp.getSignature()).getMethod();
        RateLimit     annotation = method.getAnnotation(RateLimit.class);
        HttpServletRequest  req = currentRequest();
        HttpServletResponse res = currentResponse();

        if (req == null) {
            // Not an HTTP context — skip rate limiting
            return pjp.proceed();
        }

        // ── 1. Resolve the rate limit subject ───────────────────────────────
        String subject = resolveSubject(req, annotation.keyType());
        String keyPrefix = annotation.keyPrefix().isEmpty()
                ? pjp.getSignature().toShortString().replace(" ", "")
                : annotation.keyPrefix();
        String redisKey = "rl:" + keyPrefix + ":" + subject;

        // ── 2. Resolve config (DB override takes precedence over annotation) ─
        int limit           = annotation.limit();
        int windowSeconds   = annotation.windowSeconds();
        int refillRate      = annotation.refillRatePerSecond();
        Algorithm algorithm = annotation.algorithm();
        boolean blocked     = false;

        Optional<RateLimitConfigEntity> dbConfig = configRepo.findBySubjectAndActiveTrue(subject);
        if (dbConfig.isPresent()) {
            RateLimitConfigEntity cfg = dbConfig.get();
            limit         = cfg.getLimit();
            windowSeconds = cfg.getWindowSeconds();
            refillRate    = cfg.getRefillRatePerSecond();
            algorithm     = cfg.getAlgorithm();
            blocked       = cfg.isBlocked();
        }

        // ── 3. Blocklist check ───────────────────────────────────────────────
        if (blocked) {
            log.warn("Blocked subject: {}", subject);
            persistEvent(subject, false, algorithm.name(), 0, req.getRequestURI());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(java.util.Collections.singletonMap("error",
                            "Your API key or IP has been blocked. Contact support."));
        }

        // ── 4. Rate limit check ──────────────────────────────────────────────
        RateLimitResult result = rateLimiter.check(redisKey, algorithm, limit, windowSeconds, refillRate);

        // ── 5. Add response headers (always, even on 429) ───────────────────
        if (res != null) {
            res.setHeader(HEADER_LIMIT,     String.valueOf(limit));
            res.setHeader(HEADER_RESET,     String.valueOf(result.getResetAt()));
            if (!result.isAllowed()) {
                res.setHeader(HEADER_RETRY_AFTER, String.valueOf(windowSeconds));
            }
        }

        // ── 6. Throttle or proceed ───────────────────────────────────────────
        persistEvent(subject, result.isAllowed(), algorithm.name(),
                result.getRedisLatencyMs(), req.getRequestURI());

        if (!result.isAllowed()) {
            log.debug("Throttled: key={} algorithm={}", redisKey, algorithm);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HEADER_RETRY_AFTER, String.valueOf(windowSeconds))
                    .body(java.util.Collections.singletonMap("error",
                            "Rate limit exceeded. Retry after " + windowSeconds + " seconds."));
        }

        return pjp.proceed();
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private String resolveSubject(HttpServletRequest req, LimitKeyType keyType) {
        switch (keyType) {
            case IP:
                String xff = req.getHeader("X-Forwarded-For");
                return (xff != null && !xff.isEmpty())
                        ? xff.split(",")[0].trim()
                        : req.getRemoteAddr();
            case USER:
                String user = req.getUserPrincipal() != null
                        ? req.getUserPrincipal().getName()
                        : null;
                return (user != null) ? user : resolveSubject(req, LimitKeyType.IP);
            case API_KEY:
            default:
                String apiKey = req.getHeader("X-API-Key");
                return (apiKey != null && !apiKey.isEmpty())
                        ? apiKey
                        : resolveSubject(req, LimitKeyType.IP);   // fallback to IP
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletResponse currentResponse() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getResponse();
        } catch (Exception e) {
            return null;
        }
    }

    private void persistEvent(String subject, boolean allowed, String algorithm,
                               long latencyMs, String endpoint) {
        try {
            usageRepo.save(UsageEventEntity.builder()
                    .subject(subject)
                    .allowed(allowed)
                    .algorithm(algorithm)
                    .redisLatencyMs(latencyMs)
                    .endpoint(endpoint)
                    .occurredAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to persist usage event: {}", e.getMessage());
        }
    }
}
