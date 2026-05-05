# How to Upload VaultCache to GitHub

## Step 1 — Create the Repository

1. Go to https://github.com/new
2. Fill in:
   - **Repository name:** `vaultcache`
   - **Description:** `Distributed rate limiter — Java 8, Spring AOP @RateLimit annotation, 3 atomic Redis Lua algorithms (Fixed Window, Sliding Window, Token Bucket), Micrometer/Prometheus metrics`
   - **Visibility:** Public
3. Click **Create repository**

## Step 2 — Update Placeholders

In `README.md`, replace `YOUR_USERNAME` (2 occurrences).
In `docs/DESIGN.md`, replace `<Your Name>`.
In `infra/ecs-task-definition.json`, replace `ACCOUNT_ID` (4 occurrences).

## Step 3 — Push

```bash
cd vaultcache

git init
git add .
git commit -m "feat: initial VaultCache implementation

- @RateLimit annotation: drop on any Spring MVC method to apply rate limiting
- Spring AOP @Around aspect: controller never runs on throttle (zero wasted work)
- Fixed Window Lua script: O(1), cheapest Redis footprint, simple counter
- Sliding Window Lua script: eliminates boundary burst, ZSet with timestamp scoring
- Token Bucket Lua script: burst-tolerant, HMGET/HMSET hash with refill calculation
- All Lua scripts run atomically in Redis: no TOCTOU race conditions
- Fail-open design: Redis unavailability allows requests rather than causing outage
- Live config override from PostgreSQL: change limits without redeployment
- Blocklist support: blocked subjects receive 403 with zero Redis overhead
- Subject resolution: API key header / IP address / authenticated username
- Micrometer metrics: allowed/throttled counters, Redis P50/P95/P99 latency timer
- Prometheus endpoint: /actuator/prometheus scrape-ready
- Demo endpoints: 3 public endpoints showcasing each algorithm (no JWT needed)
- Admin endpoints: top-throttled subjects, Redis key count, usage stats per subject
- PostgreSQL audit log: every request stored with latency and allow/throttle result
- Pure Mockito unit tests: zero Redis/PostgreSQL infrastructure needed for CI
- GitHub Actions CI: mvn clean test only, passes on first push"

git remote add origin https://github.com/YOUR_USERNAME/vaultcache.git
git branch -M main
git push -u origin main
```

## Step 4 — Add Topics

`java` `spring-boot` `redis` `rate-limiting` `lua` `spring-aop` `distributed-systems` `prometheus` `micrometer` `token-bucket` `sliding-window` `api-gateway`

## Step 5 — Pin to Profile

Pin `stockstream`, `creditlens`, `querymind`, and `vaultcache` on your GitHub profile.

## Conventional Commits

```
feat: add leaky bucket as fourth algorithm
feat: add IP range (CIDR) blocklisting support
perf: add local in-memory cache for hot subjects to reduce Redis calls
test: add integration test against embedded Redis
docs: add Grafana dashboard JSON to infra/
fix: handle ZADD uniqueness in sliding window for identical timestamps
```
