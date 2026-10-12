# Rate Limiter — Distributed Token Bucket with Redis & Lua

## A. Purpose

This project implements a production-grade, distributed **Token Bucket Rate Limiter** using Spring Boot, Redis, and an atomic Redis Lua script. The primary system design concept demonstrated is **Per-Client Rate Limiting with Atomic Token Replenishment**, protecting downstream services against traffic spikes, abuse, and Denial-of-Service attacks. Each client is identified via an `X-Client-Id` HTTP header and maintains an independent token bucket stored in Redis. When incoming requests exceed the configured burst capacity, the application immediately rejects surplus requests with **HTTP 429 Too Many Requests** and a `Retry-After` header.

```
 Client Request [X-Client-Id: clientA]
                   │
                   ▼
       Spring Boot Controller (GET /api/test)
                   │
                   ▼
          RedisRateLimiter.java
                   │ (Atomic eval of rate_limiter.lua)
                   ▼
  Redis Hash [rate_limit:clientA]
  - tokens: 9.0
  - last_refill_time: 1718000000000
                   │
         ┌─────────┴─────────┐
         ▼                   ▼
   [tokens >= 1]       [tokens < 1]
    Consume 1           Rejected (429)
    HTTP 200 OK         Retry-After: <sec>
```

---

## B. Prerequisites and setup

### Required Tools & Versions
- **Java JDK**: 17 or higher (`java -version`)
- **Maven**: 3.6+ or included Maven Wrapper (`mvnw.cmd` / `./mvnw`)
- **Docker Engine**: CLI runtime for Redis container (`docker --version`)

### Step-by-Step Setup Commands

#### 1. Navigate to Project Directory
- **PowerShell (Windows)**:
  ```powershell
  cd c:\Users\sandh\Documents\sem5\System-design\RateLimiter
  ```
- **Bash (Linux/macOS)**:
  ```bash
  cd RateLimiter
  ```

#### 2. Start the Redis Container
The project includes a `docker-compose.yml` configuring Redis 7:
- **PowerShell / Bash**:
  ```bash
  docker compose up -d
  ```

#### 3. Verify Redis is Running
- **PowerShell / Bash**:
  ```bash
  docker ps --filter "name=rate-limiter-redis"
  ```
  Expected: Container `rate-limiter-redis` listening on port `0.0.0.0:6379->6379/tcp`.

---

## C. Docker setup (terminal only)

### Docker Engine Verification
- **PowerShell**:
  ```powershell
  docker version
  docker ps
  ```

### Container Lifecycle & Inspection Commands
- **Service in `docker-compose.yml`**: `redis` (image: `redis:7-alpine`, container: `rate-limiter-redis`).
- **Check container logs**:
  ```bash
  docker logs rate-limiter-redis --tail 20
  ```
- **Inspect Redis internal keys using `redis-cli`**:
  ```bash
  docker exec -it rate-limiter-redis redis-cli ping
  docker exec -it rate-limiter-redis redis-cli keys "rate_limit:*"
  ```
- **Stop Redis service**:
  ```bash
  docker compose stop
  ```
- **Tear down Redis container and clean volumes**:
  ```bash
  docker compose down -v
  ```

---

## D. Run the application

- **Working Directory**: `RateLimiter/`
- **Entry Point**: `com.example.ratelimiter.RateLimiterApplication`
- **Configuration File**: `src/main/resources/application.properties`
  - `spring.data.redis.host=localhost`
  - `spring.data.redis.port=6379`
  - `rate-limit.capacity=10` *(Maximum bucket capacity / burst limit)*
  - `rate-limit.refill-rate=2` *(Replenishment rate: 2 tokens per second)*
- **Default Port & Base URL**: `http://localhost:8080`

### Start Commands
- **PowerShell (Windows)**:
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```
- **Bash (Linux)**:
  ```bash
  ./mvnw spring-boot:run
  ```
- **Alternative (via system Maven)**:
  ```bash
  mvn spring-boot:run
  ```

### Expected Startup Output
```text
Tomcat started on port 8080 (http) with context path '/'
Started RateLimiterApplication in X.XXX seconds
```

### Stop the Application
Press `Ctrl + C` in the running terminal.

---

## E. API testing with Postman (where applicable)

### Implemented Endpoints

| HTTP Method | Full Local URL | Required Headers | Example Request Body | Expected Status | Purpose |
|---|---|---|---|---|---|
| `GET` | `http://localhost:8080/api/test` | `X-Client-Id: client-123` | *None* | `200 OK` | Verify request within rate limit and consume 1 token |
| `GET` | `http://localhost:8080/api/test` | `X-Client-Id: client-123` *(burst)* | *None* | `429 Too Many Requests` | Trigger rate limiting when tokens are exhausted |
| `GET` | `http://localhost:8080/api/test` | *None (Missing Header)* | *None* | `400 Bad Request` | Validate client ID requirement |

### Copy-Pasteable Postman Test Cases

#### 1. Success Case (Allowed Request)
- **URL**: `http://localhost:8080/api/test`
- **Method**: `GET`
- **Header**: `X-Client-Id`: `client-100`
- **Expected Status**: `200 OK`
- **Expected Response (JSON)**:
  ```json
  {
    "allowed": true,
    "clientId": "client-100",
    "remainingTokens": 9,
    "message": "Request allowed"
  }
  ```

#### 2. Rate Limited Case (Burst Exhaustion)
Send 11 rapid requests with `X-Client-Id: client-burst`:
- **URL**: `http://localhost:8080/api/test`
- **Method**: `GET`
- **Header**: `X-Client-Id`: `client-burst`
- **Expected Status (11th Request)**: `429 Too Many Requests`
- **Expected Response Headers**: `Retry-After: 1`
- **Expected Response (JSON)**:
  ```json
  {
    "allowed": false,
    "clientId": "client-burst",
    "remainingTokens": 0,
    "message": "Rate limit exceeded"
  }
  ```

#### 3. Error Case (Missing Client Header)
- **URL**: `http://localhost:8080/api/test`
- **Method**: `GET`
- **Header**: *(Omit `X-Client-Id`)*
- **Expected Status**: `400 Bad Request`
- **Expected Response (JSON)**:
  ```json
  {
    "allowed": false,
    "clientId": null,
    "remainingTokens": 0,
    "message": "Missing client identifier header 'X-Client-Id'"
  }
  ```

---

## F. Implementation explained

### Source Files & Responsibilities

| File Path | Class / Script | Responsibility |
|---|---|---|
| `.../resources/scripts/rate_limiter.lua` | Redis Lua Script | Atomically fetches token state, computes continuous time-based refill, checks capacity, decrements token count, and updates Redis Hash with 1-hour expiration. |
| `.../config/RateLimitProperties.java` | `RateLimitProperties` | Binds `rate-limit.capacity` and `rate-limit.refill-rate` configuration values. |
| `.../config/RedisConfig.java` | `RedisConfig` | Configures `RedisScript<List>` bean pointing to `scripts/rate_limiter.lua`. |
| `.../redis/RedisRateLimiter.java` | `RedisRateLimiter` | Executes Lua script via `StringRedisTemplate`. Implements **fail-open** design: if Redis is unreachable, allows traffic rather than taking down service. |
| `.../service/RateLimiterService.java` | `RateLimiterService` | Service layer orchestrating rate-check calls and logging metrics. |
| `.../controller/TestController.java` | `TestController` | Validates `X-Client-Id` header, invokes limiter, and formats HTTP 200 or 429 response with `Retry-After`. |

### Core Algorithm & Request Flow

1. **State Storage**:
   Each client possesses a Redis Hash with key `rate_limit:<clientId>` holding two fields:
   - `tokens`: Current floating-point count of tokens in bucket.
   - `last_refill_time`: Epoch millisecond timestamp of previous request.

2. **Continuous Mathematical Refill (No Periodic Background Ticks)**:
   Refill is calculated lazily upon request arrival inside `rate_limiter.lua`:
   $$\text{elapsed} = \frac{\text{now} - \text{last\_refill\_time}}{1000.0}$$
   $$\text{current\_tokens} = \min(\text{capacity}, \text{last\_tokens} + (\text{elapsed} \times \text{refill\_rate}))$$

3. **Atomicity via Lua**:
   Because Lua scripts run atomically in Redis:
   - If `current_tokens >= requested`:
     - $\text{remaining} = \text{current\_tokens} - \text{requested}$
     - Updates Redis: `HMSET key tokens <remaining> last_refill_time <now>`
     - Sets TTL: `EXPIRE key 3600`
     - Returns `allowed = 1`, `remaining`, `wait_time = 0`
   - If `current_tokens < requested`:
     - $\text{wait\_time} = \frac{\text{requested} - \text{current\_tokens}}{\text{refill\_rate}}$
     - Returns `allowed = 0`, `remaining`, `wait_time`

---

## G. Verification and expected results

### 1. Terminal Burst Test Verification
Send 15 requests in a rapid loop from PowerShell or Bash:

- **PowerShell**:
  ```powershell
  1..15 | ForEach-Object {
      try {
          $res = Invoke-RestMethod -Uri "http://localhost:8080/api/test" -Headers @{"X-Client-Id"="burst-test"}
          Write-Host "$_ : HTTP 200 - Remaining: $($res.remainingTokens)" -ForegroundColor Green
      } catch {
          Write-Host "$_ : HTTP 429 - Rate Limited" -ForegroundColor Red
      }
  }
  ```
- **Bash**:
  ```bash
  for i in {1..15}; do
    curl -s -o /dev/null -w "%{http_code}\n" -H "X-Client-Id: burst-test" http://localhost:8080/api/test
  done
  ```
**Expected Output**:
- The first 10 requests return `200` (bucket capacity starts at 10).
- Requests 11 through 15 return `429` (bucket depleted).

### 2. Inspect Redis Key State via `redis-cli`
Inspect the internal bucket state stored in Redis:
```bash
docker exec -it rate-limiter-redis redis-cli HGETALL "rate_limit:burst-test"
```
**Expected Output**:
```text
1) "tokens"
2) "0"
3) "last_refill_time"
4) "1718000000000"
```

---

## H. JMeter testing (where applicable)

### JMeter Test Plan Setup for `GET /api/test`

1. **Create Thread Group**:
   - Number of Threads (Users): `20`
   - Ramp-up Period (seconds): `1` (simulates concurrent burst)
   - Loop Count: `1`
2. **Add HTTP Header Manager**:
   - Name: `X-Client-Id`
   - Value: `jmeter-test-client`
3. **Add HTTP Request Sampler**:
   - Protocol: `http`
   - Server Name: `localhost`
   - Port: `8080`
   - Method: `GET`
   - Path: `/api/test`
4. **Add Listeners**:
   - `View Results Tree`
   - `Aggregate Report`
5. **Expected Results & Metrics to Inspect**:
   - Total Requests: 20
   - Exactly **10 requests** succeed with response code `200`.
   - Exactly **10 requests** fail with response code `429`.
   - Error % in Aggregate Report: **50.00%** (confirming strict atomic token enforcement).

---

## I. Common errors and quick fixes

1. **Error**: `RedisConnectionFailureException: Unable to connect to Redis; nested exception is io.lettuce.core.RedisConnectionException`
   - **Cause**: Redis container is not running on port 6379.
   - **Fix**: Run `docker compose up -d` in the `RateLimiter` directory.
   - *Note*: Because of the fail-open fallback, the application will not crash; it logs an error and allows requests with capacity=10.
2. **Error**: `Port 8080 already in use`
   - **Cause**: Another service (`ConsistentHashing` or `autocomplete-system`) is occupying port 8080.
   - **Fix**: Terminate the other process or set `server.port=8083` in `application.properties`.
3. **Error**: `400 Bad Request: Missing client identifier header 'X-Client-Id'`
   - **Cause**: The test request did not include the `X-Client-Id` header.
   - **Fix**: Add `-H "X-Client-Id: <id>"` in curl or Postman headers.

---

## J. Exam quick revision

### Clean-Start Command Sequence
```powershell
# 1. Start Redis
docker compose up -d

# 2. Run Spring Boot
.\mvnw.cmd spring-boot:run

# 3. Test single request (PowerShell)
Invoke-RestMethod -Uri "http://localhost:8080/api/test" -Headers @{"X-Client-Id"="exam-user"}
```

### 3 Key Files / Methods to Remember
1. `rate_limiter.lua`: Atomic token refill and deduction script.
2. `RedisRateLimiter.java`: `isAllowed(clientId, tokensRequested)` with fail-open fallback.
3. `TestController.java`: Header parsing and HTTP 429 response with `Retry-After`.

### Token Bucket Logic in 3 Steps
1. Retrieve `tokens` and `last_refill_time` from Redis hash `rate_limit:<id>`.
2. Refill tokens continuously: $\min(\text{capacity}, \text{tokens} + \text{elapsed} \times \text{refill\_rate})$.
3. If $\ge 1$ token available, decrement by 1 and return HTTP 200; else return HTTP 429 with calculated wait time.

### Likely Viva Questions & Answers
- **Q1: Why use Token Bucket instead of Fixed Window Counter?**
  *A*: Fixed Window suffers from traffic boundary bursts (e.g., $2\times$ the limit across window boundaries). Token Bucket smooths traffic while permitting controlled bursts up to bucket capacity.
- **Q2: Why execute the rate limiter logic inside a Redis Lua script?**
  *A*: Redis executes Lua scripts atomically as a single command, preventing race conditions (read-modify-write) between concurrent distributed application servers without requiring distributed locks.
- **Q3: What is "Fail-Open" and why is it used here?**
  *A*: If Redis goes down, fail-open permits traffic through instead of failing every incoming user request, trading rate-limit enforcement for overall system availability.

### Cleanup Commands
```powershell
docker compose down -v
```

---

## Known Issues / Setup Blockers

1. **Port 8080 Collision Potential**:
   - *Location*: `src/main/resources/application.properties` (does not specify `server.port`, defaulting to 8080).
   - *Exam Impact*: If `ConsistentHashing` or `autocomplete-system` is running simultaneously on port 8080, `RateLimiter` will fail on startup with `Web server failed to start. Port 8080 was already in use`. Make sure to stop other services before launching `RateLimiter`.
2. **Fail-Open Behavior Masking Redis Outages**:
   - *Location*: `RedisRateLimiter.java` (lines 62–66).
   - *Exam Impact*: If Redis fails to start or crashes, requests will continue returning HTTP 200 instead of failing. Verify Redis is actively running via `docker ps` before conducting tests.
