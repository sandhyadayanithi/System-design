# Rate Limiter — Token Bucket with Redis & Lua

A production-style, per-client **Token Bucket Rate Limiter** built with **Spring Boot**, **Redis**, and an **atomic Redis Lua script**.

---

## Project Overview

This project demonstrates how to build a distributed rate limiter from scratch, without relying on libraries like Bucket4j or Spring Cloud Gateway.

Every client gets its own independent token bucket stored in Redis:

```
Client A → Bucket A (key: rate_limit:clientA) → 10 tokens
Client B → Bucket B (key: rate_limit:clientB) → 10 tokens
Client C → Bucket C (key: rate_limit:clientC) → 10 tokens
```

Incoming requests consume one token. When a bucket is empty, the request is rejected with **HTTP 429 Too Many Requests**. Tokens are continuously replenished based on elapsed real time.

---

## Token Bucket Algorithm

### How It Works

| Concept | Description |
|---|---|
| **Capacity** | Maximum number of tokens the bucket can hold |
| **Refill Rate** | Tokens added per second (continuously, not in batches) |
| **Token Consumption** | Each request consumes 1 token |
| **Token Refill** | `tokens = min(capacity, storedTokens + elapsed × refillRate)` |
| **Burst Traffic** | Burst up to `capacity` requests allowed immediately |

### Example

```
Capacity  = 10 tokens
Refill    = 2 tokens/sec
```

**Burst phase**: A client can fire 10 requests immediately — all succeed because the bucket starts full.

**Steady state**: After the burst, new tokens arrive at 2/sec. The client can sustain 2 requests/sec indefinitely.

**Token refill is calculated from elapsed time** — not from a scheduled background timer. When a new request arrives:

```
elapsed        = currentTime - lastRefillTime  (milliseconds)
tokensToAdd    = elapsed / 1000 * refillRate
newTokenCount  = min(capacity, storedTokens + tokensToAdd)
```

This means tokens are refilled accurately regardless of when requests arrive.

---

## Why Redis?

- **Shared state**: When multiple Spring Boot instances run (horizontal scaling), each instance reads and writes to the same Redis key — no split-brain.
- **Low latency**: Redis operations are sub-millisecond.
- **Persistence**: Rate limit state survives application restarts (keys auto-expire after 1 hour of inactivity).
- **Atomic operations**: Redis executes Lua scripts atomically, making concurrent token consumption safe.

---

## Why a Lua Script?

Without atomicity, two concurrent requests could both read `tokens = 1`, both decide "allowed", and both decrement — resulting in `tokens = -1` and two requests passing when only one should.

The Lua script runs inside Redis as a **single atomic operation**:

```
1. Read tokens + lastRefillTime
2. Calculate elapsed time
3. Calculate tokens to add based on elapsed time
4. Cap tokens at capacity
5. If tokens >= 1 → decrement, save state, return ALLOWED
6. Else → return REJECTED + wait time
```

This is equivalent to a database transaction — no other Redis command runs between steps 1 and 6.

---

## Project Structure

```
com.example.ratelimiter
├── RateLimiterApplication.java
├── config/
│   ├── RateLimitProperties.java     ← Binds application.properties settings
│   └── RedisConfig.java             ← Registers the Lua script as a Spring bean
├── controller/
│   └── TestController.java          ← GET /api/test endpoint
├── service/
│   └── RateLimiterService.java      ← Coordinates rate limit check + logging
├── redis/
│   └── RedisRateLimiter.java        ← Executes Lua script via StringRedisTemplate
├── model/
│   ├── RateLimitResult.java         ← Internal result (allowed, remaining, waitTime)
│   └── ApiResponse.java             ← JSON response body
└── exception/
    ├── MissingClientIdException.java
    └── GlobalExceptionHandler.java
```

**Lua script**: `src/main/resources/scripts/rate_limiter.lua`

---

## Configuration

```properties
# application.properties

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Rate Limiter
rate-limit.capacity=10      # Max tokens per bucket
rate-limit.refill-rate=2    # Tokens added per second
```

| Property | Meaning | Default |
|---|---|---|
| `rate-limit.capacity` | Maximum burst size | `10` |
| `rate-limit.refill-rate` | Tokens added per second | `2` |

---

## Running Redis

Redis runs as a Docker container — no local installation required.

```bash
# Start Redis (in the background)
docker compose up -d

# Stop Redis
docker compose down
```

Verify it is running:

```bash
docker ps
```

Expected output:
```
CONTAINER ID   IMAGE            PORTS                    NAMES
...            redis:7-alpine   0.0.0.0:6379->6379/tcp   rate-limiter-redis
```

---

## Running Spring Boot

```bash
./mvnw spring-boot:run
```

Or on Windows:

```cmd
mvnw.cmd spring-boot:run
```

The application starts on **http://localhost:8080**.

---

## API Usage

### Endpoint

```
GET /api/test
Header: X-Client-Id: <your-client-id>
```

### Successful request (HTTP 200)

```bash
curl -H "X-Client-Id: client123" http://localhost:8080/api/test
```

```json
{
  "allowed": true,
  "clientId": "client123",
  "remainingTokens": 9,
  "message": "Request allowed"
}
```

### Rate limited (HTTP 429)

```json
{
  "allowed": false,
  "clientId": "client123",
  "remainingTokens": 0,
  "message": "Rate limit exceeded"
}
```

Response headers on 429:
```
Retry-After: 1
```

### Missing header (HTTP 400)

```bash
curl http://localhost:8080/api/test
```

```json
{
  "message": "Missing client identifier header 'X-Client-Id'"
}
```

---

## Testing Normal Traffic

With `refill-rate=2`, one request per second is always within limit:

```bash
# Send 1 request every second — all should succeed indefinitely
for i in {1..20}; do
  curl -s -H "X-Client-Id: client-normal" http://localhost:8080/api/test | python -m json.tool
  sleep 1
done
```

---

## Testing Burst Traffic

Send 15 requests immediately. The first 10 succeed (bucket starts full), the next 5 are rejected:

```bash
# Bash / Git Bash
for i in {1..15}; do
  curl -s -o /dev/null -w "%{http_code}\n" -H "X-Client-Id: burst-client" http://localhost:8080/api/test
done
```

Expected output:
```
200
200
200
200
200
200
200
200
200
200
429
429
429
429
429
```

After waiting ~5 seconds (5 × 2 refill = 10 tokens), the bucket refills and requests are allowed again.

---

## Testing Concurrent Traffic

Using `curl` with parallel jobs (Bash):

```bash
for i in {1..20}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -H "X-Client-Id: concurrent-client" \
    http://localhost:8080/api/test &
done
wait
```

Or on Windows with PowerShell:

```powershell
$jobs = 1..20 | ForEach-Object {
    Start-Job {
        Invoke-WebRequest -Uri "http://localhost:8080/api/test" `
          -Headers @{"X-Client-Id"="concurrent-client"} `
          -ErrorAction SilentlyContinue | Select-Object -ExpandProperty StatusCode
    }
}
$jobs | Wait-Job | Receive-Job
```

Expected: **exactly 10 × `200`** and **10 × `429`** — regardless of arrival order.

The Redis Lua script guarantees this atomicity even when multiple Spring Boot instances are running simultaneously.

---

## Running Tests

```bash
./mvnw test
```

Tests included:

| Test | Description |
|---|---|
| `testInitialRequestsAllowedAndBurstRejected` | First 10 requests pass, 11th gets HTTP 429 |
| `testTokenRefillOverTime` | After draining bucket, wait 1.1s and verify refill |
| `testDifferentClientsHaveIndependentBuckets` | clientA exhausted does not affect clientB |
| `testMissingClientIdHeaderReturnsBadRequest` | Missing header → HTTP 400 |
| `testConcurrentRequestsAtomicityViaLua` | 20 threads race — exactly 10 pass, 10 rejected |
