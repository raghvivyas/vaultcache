# VaultCache 🔒

> Distributed rate limiter and API gateway library — Java 8 · Spring AOP `@RateLimit` annotation · Three Lua-atomic Redis algorithms · Micrometer/Prometheus metrics · AWS ECS.

**Why this stands out in interviews:** Building a production-quality rate limiter from scratch — using atomic Lua scripts in Redis — is exactly the kind of deep infrastructure thinking that separates senior from mid-level engineers. Interviewers at Razorpay, PhonePe, or any high-traffic company will spend 20 minutes on this project.

[![CI](https://github.com/raghvivyas/vaultcache/actions/workflows/ci.yml/badge.svg)](https://github.com/raghvivyas/vaultcache/actions)
[![Java 8](https://img.shields.io/badge/Java-8-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen.svg)](https://spring.io/projects/spring-boot)

---

## Architecture

```
@RateLimit annotation on any Spring MVC method
         │
         ▼
  RateLimitAspect (@Around AOP)
    │   Resolves subject (API key / IP / username)
    │   Looks up live config from PostgreSQL
    │   Checks blocklist
    ▼
  RateLimiterService
    │   Executes atomic Lua script in Redis
    │   Records Micrometer metric
    ▼
  Decision: ALLOW → proceed to controller
            THROTTLE → return HTTP 429 (controller never runs)
            BLOCKED  → return HTTP 403
```

**Three algorithms — one annotation:**

| Algorithm | Redis Structure | Best For |
|-----------|----------------|----------|
| `FIXED_WINDOW` | String counter | Internal APIs, cheap, simplest |
| `SLIDING_WINDOW` | Sorted Set (ZSet) | Public APIs, eliminates boundary burst |
| `TOKEN_BUCKET` | Hash | Burst-tolerant endpoints, batch operations |

---

## Quick Start (2 commands)

```bash
git clone https://github.com/raghvivyas/vaultcache.git
cd vaultcache
docker-compose up --build
```

App starts at **http://localhost:8080**. Default users seeded:

| Username | Password    | Role  |
|----------|-------------|-------|
| admin    | password123 | ADMIN |
| demo     | password123 | USER  |

---

## Using the @RateLimit Annotation

Drop it on any Spring MVC method:

```java
// 100 req/min per API key — sliding window (most accurate)
@RateLimit(limit = 100, windowSeconds = 60, algorithm = Algorithm.SLIDING_WINDOW)
@GetMapping("/api/data")
public ResponseEntity<Data> getData() { ... }

// 10 req/min per IP — for login endpoints
@RateLimit(limit = 10, windowSeconds = 60,
           keyType = LimitKeyType.IP, algorithm = Algorithm.FIXED_WINDOW)
@PostMapping("/api/auth/login")
public ResponseEntity<?> login() { ... }

// Token bucket: 50 capacity, refills 5 tokens/second — for expensive operations
@RateLimit(limit = 50, refillRatePerSecond = 5, algorithm = Algorithm.TOKEN_BUCKET)
@PostMapping("/api/export")
public ResponseEntity<?> exportData() { ... }
```

The aspect intercepts before the controller runs. **If throttled, the controller method never executes** — no wasted database queries or API calls.

Response headers added on every request:
```
X-RateLimit-Limit:  100
X-RateLimit-Reset:  1705312860
Retry-After:        60     (only on 429)
```

---

## Demo — Watch Rate Limiting in Action

### Sliding window (10 req/60s per API key)

```bash
# Send 15 requests — first 10 pass, last 5 get 429
for i in $(seq 1 15); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-API-Key: test-key-123" \
    http://localhost:8080/api/demo/sliding)
  echo "Request $i: HTTP $STATUS"
done
```

Expected output:
```
Request 1:  HTTP 200
Request 2:  HTTP 200
...
Request 10: HTTP 200
Request 11: HTTP 429  ← throttled
Request 12: HTTP 429
...
```

### Token bucket (20 capacity, 2 tokens/second)

```bash
# Burst: send 25 rapidly — first 20 pass, then throttled
for i in $(seq 1 25); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-API-Key: burst-key" \
    http://localhost:8080/api/demo/token-bucket)
  echo "Burst request $i: HTTP $STATUS"
done

# Wait 5 seconds — 10 tokens refill, next 10 requests pass
sleep 5
for i in $(seq 1 10); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-API-Key: burst-key" \
    http://localhost:8080/api/demo/token-bucket)
  echo "After-wait $i: HTTP $STATUS"
done
```

### Fixed window

```bash
# 5 req / 30 seconds per API key
for i in $(seq 1 8); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-API-Key: fixed-key" \
    http://localhost:8080/api/demo/fixed)
  echo "Request $i: HTTP $STATUS"
done
```

### IP-based (no API key needed)

```bash
for i in $(seq 1 25); do
  curl -s -o /dev/null -w "Request $i: %{http_code}\n" \
    http://localhost:8080/api/demo/by-ip
done
```

---

## Admin API

### Get a JWT token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}' | jq -r '.data.token')
```

### Create a custom rate limit config

```bash
# Override the default for a specific API key (takes effect immediately, no redeploy)
curl -s -X POST http://localhost:8080/api/configs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "subject":             "enterprise-api-key-abc123",
    "keyType":             "API_KEY",
    "algorithm":           "TOKEN_BUCKET",
    "limit":               5000,
    "windowSeconds":       60,
    "refillRatePerSecond": 50,
    "tier":                "ENTERPRISE"
  }' | jq .
```

### List all configs

```bash
curl -s http://localhost:8080/api/configs \
  -H "Authorization: Bearer $TOKEN" | jq '.data[] | {subject, algorithm, limit, tier}'
```

### Block a subject (instant 403, no Redis check)

```bash
curl -s -X POST http://localhost:8080/api/configs/1/block \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### View usage stats for a subject

```bash
curl -s "http://localhost:8080/api/configs/stats/test-key-123" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

```json
{
  "data": {
    "subject":            "test-key-123",
    "totalAllowed":       847,
    "totalThrottled":     153,
    "throttleRatePct":    15.3,
    "avgRedisLatencyMs":  1.24,
    "lastSeenAt":         "2024-01-15T10:35:22Z"
  }
}
```

### Top throttled subjects

```bash
curl -s "http://localhost:8080/api/admin/top-throttled?limit=5" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### Live Redis key count

```bash
curl -s http://localhost:8080/api/admin/redis-info \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### Prometheus metrics

```bash
curl -s http://localhost:8080/actuator/prometheus | grep vaultcache
```

```
vaultcache_requests_allowed_total 847.0
vaultcache_requests_throttled_total 153.0
vaultcache_redis_latency_seconds{quantile="0.5"} 0.0012
vaultcache_redis_latency_seconds{quantile="0.99"} 0.0024
```

---

## Running Without Docker

```bash
# Start Redis and PostgreSQL only
docker-compose up redis postgres -d

cp .env.example .env
export $(cat .env | grep -v '#' | xargs)
./mvnw spring-boot:run
```

---

## Running Tests

```bash
# All unit tests — no Redis, no PostgreSQL required
./mvnw test

# With coverage
./mvnw verify
```

All tests are pure Mockito — the Redis Lua scripts are tested by mocking `redisTimer.record()`. The annotation reflection tests verify the annotation is correctly configured with the right defaults.

---

## Project Structure

```
vaultcache/
├── src/main/java/com/vaultcache/
│   ├── VaultCacheApplication.java
│   ├── annotation/
│   │   └── RateLimit.java               # ★ The @RateLimit annotation
│   ├── aspect/
│   │   └── RateLimitAspect.java         # ★ Spring AOP @Around intercept
│   ├── config/                          # AppProperties, RedisConfig (Lua scripts), Security, Micrometer
│   ├── model/                           # Algorithm, LimitKeyType, RateLimitResult, DTOs
│   ├── entity/                          # RateLimitConfigEntity, UsageEventEntity, UserEntity
│   ├── repository/                      # JPA repos with analytics queries
│   ├── service/
│   │   ├── RateLimiterService.java      # ★ Executes Lua scripts, records metrics
│   │   ├── RateLimitConfigService.java  # Admin CRUD + Redis key invalidation
│   │   └── AuthService.java
│   ├── controller/
│   │   ├── DemoController.java          # ★ Public demo endpoints (all 3 algorithms)
│   │   ├── RateLimitConfigController.java
│   │   ├── AdminController.java         # Dashboard, top-throttled, Redis info
│   │   └── AuthController.java
│   ├── security/                        # JWT provider, filter, UserDetailsService
│   ├── exception/                       # GlobalExceptionHandler
│   └── scheduler/                       # DataInitializer
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/V1__create_tables.sql
├── src/test/java/com/vaultcache/
│   ├── VaultCacheApplicationTests.java
│   ├── service/RateLimiterServiceTest.java    # Mocked Redis, tests all 3 algorithms
│   ├── aspect/RateLimitAnnotationTest.java    # Reflection tests on @RateLimit
│   └── controller/DemoControllerTest.java    # Pure unit tests, verifies annotation config
├── docs/DESIGN.md                        # Algorithm analysis, Lua atomicity argument, benchmarks
├── infra/
│   ├── ecs-task-definition.json
│   └── ecr-push.sh
├── Dockerfile
├── docker-compose.yml
└── .github/workflows/ci.yml             # Pure unit tests, no infrastructure
```

---

## Key Design Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Atomicity | Lua scripts | Eliminates TOCTOU race; simpler than MULTI/EXEC retries |
| AOP placement | `@Around` before controller | Controller never runs on throttle; zero wasted work |
| Config storage | PostgreSQL | Survives restarts; live updates without redeploy |
| Redis failure | Fail open | Redis blip must not cause API outage |
| Metrics | Micrometer + Prometheus | Industry standard; directly importable to Grafana |
| Redis eviction | `allkeys-lru` | Prevent OOM; old rate limit keys are safe to evict |

---

## Deploying to AWS

```bash
# 1. Store secrets
aws ssm put-parameter --name /vaultcache/DB_PASSWORD --value "your-rds-pass"  --type SecureString
aws ssm put-parameter --name /vaultcache/JWT_SECRET  --value "$(openssl rand -hex 32)" --type SecureString
aws ssm put-parameter --name /vaultcache/REDIS_HOST  --value "your-elasticache-endpoint" --type SecureString

# 2. Push image
./infra/ecr-push.sh YOUR_ACCOUNT_ID ap-south-1

# 3. Deploy
aws ecs register-task-definition --cli-input-json file://infra/ecs-task-definition.json
aws ecs update-service --cluster vaultcache --service vaultcache-svc --force-new-deployment
```

---

## About

VaultCache demonstrates infrastructure-level engineering in Java — the patterns used by API gateway teams at Razorpay, Zerodha, and AWS API Gateway itself. The Lua atomicity argument, the fail-open design, and the AOP annotation approach are exactly the topics that come up in senior system design interviews.
