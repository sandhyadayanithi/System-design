# URL Shortener with Redis Cache-Aside & MongoDB

## A. Purpose

This project extends the URL Shortener architecture by introducing a high-performance **Redis Cache-Aside Layer** alongside MongoDB. The main system design concept demonstrated is the **Cache-Aside (Lazy Loading) Caching Pattern** combined with **Database Offloading for Read-Heavy Workloads**. While new URL mappings are persisted into MongoDB, redirect lookups (`GET /{shortCode}`) inspect the in-memory Redis cache first; on a cache miss, the service queries MongoDB, populates the Redis cache with the mapping, and returns the destination URL with HTTP 302 redirection.

```
 Client Request (GET /{shortCode})
                 │
                 ▼
     urlService.getLongUrl(shortCode)
                 │
                 ▼
   Check Redis (opsForValue.get)
                 │
        ┌────────┴────────┐
        ▼                 ▼
   CACHE HIT         CACHE MISS
  Return longUrl   Query MongoDB repo.findByShortUrl()
   (Sub-ms latency)       │
                          ▼
                 Found in MongoDB?
                 ┌────────┴────────┐
                 ▼                 ▼
                YES                NO
          Write to Redis        Return 404
        opsForValue.set()
        Return longUrl (302)
```

---

## B. Prerequisites and setup

### Required Tools & Versions
- **Java JDK**: 17 or higher (`java -version`)
- **Maven**: 3.6+ or included Maven Wrapper (`mvnw.cmd` / `./mvnw`)
- **Docker Engine**: CLI runtime for running MongoDB and Redis (`docker --version`)

### Step-by-Step Setup Commands

#### 1. Navigate to Project Directory
- **PowerShell (Windows)**:
  ```powershell
  cd c:\Users\sandh\Documents\sem5\System-design\UrlShortenerRedis
  ```
- **Bash (Linux/macOS)**:
  ```bash
  cd UrlShortenerRedis
  ```

#### 2. Start Required Services (MongoDB & Redis Containers)
- **PowerShell (Windows)**:
  ```powershell
  docker run -d --name url-redis -p 6379:6379 redis:7-alpine
  docker run -d --name url-mongo -p 27017:27017 mongo:latest
  ```
- **Bash (Linux/macOS)**:
  ```bash
  docker run -d --name url-redis -p 6379:6379 redis:7-alpine
  docker run -d --name url-mongo -p 27017:27017 mongo:latest
  ```

#### 3. Verify Both Services are Running
- **PowerShell / Bash**:
  ```bash
  docker ps --filter "name=url-"
  ```
  Expected: Containers `url-redis` (6379) and `url-mongo` (27017) are running.

---

## C. Docker setup (terminal only)

### Docker Engine Verification
- **PowerShell**:
  ```powershell
  docker version
  docker ps
  ```

### Inspecting Redis and MongoDB from Terminal
- **Check Redis connection via `redis-cli`**:
  ```bash
  docker exec -it url-redis redis-cli ping
  ```
- **Inspect cached keys in Redis**:
  ```bash
  docker exec -it url-redis redis-cli KEYS "*"
  ```
- **Inspect MongoDB documents**:
  ```bash
  docker exec -it url-mongo mongosh urlshortenerdb --eval "db.urlMapping.find()"
  ```
- **Stop and remove containers**:
  ```bash
  docker stop url-redis url-mongo
  docker rm url-redis url-mongo
  ```

---

## D. Run the application

- **Working Directory**: `UrlShortenerRedis/`
- **Entry Point**: `com.example.urlshortener.UrlShortenerApplication`
- **Configuration File**: `src/main/resources/application.properties`
  - `spring.application.name=UrlShortener`
  - `spring.data.mongodb.uri=mongodb://localhost:27017/urlshortenerdb`
  - `spring.data.redis.host=localhost`
  - `spring.data.redis.port=6379`
  - `server.port=8081`
- **Default Port & Base URL**: `http://localhost:8081`

### Start Commands
- **PowerShell (Windows)**:
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```
- **Bash (Linux)**:
  ```bash
  ./mvnw spring-boot:run
  ```

### Expected Startup Output
```text
Tomcat started on port 8081 (http) with context path '/'
Started UrlShortenerApplication in X.XXX seconds
```

### Stop the Application
Press `Ctrl + C` in the running terminal.

---

## E. API testing with Postman (where applicable)

### Implemented Endpoints

| HTTP Method | Full Local URL | Required Headers | Example Request Body | Expected Status | Purpose |
|---|---|---|---|---|---|
| `POST` | `http://localhost:8081/shorten` | `Content-Type: application/json` | `{"longUrl":"https://spring.io"}` | `200 OK` | Create short URL (writes to MongoDB only) |
| `GET` | `http://localhost:8081/{shortCode}` *(1st call)* | None | None | `302 Found` | Cache Miss: Fetches from DB, caches in Redis, redirects |
| `GET` | `http://localhost:8081/{shortCode}` *(2nd call)* | None | None | `302 Found` | Cache Hit: Fetches directly from Redis, redirects |
| `GET` | `http://localhost:8081/nonexistent` | None | None | `404 Not Found` | Cache Miss + DB Miss: returns 404 |

### Copy-Pasteable Postman Test Cases

#### 1. Success Case: Shorten URL
- **URL**: `http://localhost:8081/shorten`
- **Method**: `POST`
- **Headers**: `Content-Type: application/json`
- **Body**:
  ```json
  {
    "longUrl": "https://docs.docker.com"
  }
  ```
- **Expected Status**: `200 OK`
- **Expected Response (JSON)**:
  ```json
  {
    "shortUrl": "http://localhost:8081/a3e1f0"
  }
  ```

#### 2. Success Case: Cache Miss followed by Cache Hit (Observe Console Logs)
In Postman, send two consecutive `GET` requests to `http://localhost:8081/a3e1f0` (with *Follow Redirects* disabled):
- **Call 1**: Returns `302 Found` with `Location: https://docs.docker.com`. Console logs:
  ```text
  CACHE MISS
  ```
- **Call 2**: Returns `302 Found` with `Location: https://docs.docker.com`. Console logs:
  ```text
  CACHE HIT
  ```

#### 3. Failure Case: Invalid Short Code
- **URL**: `http://localhost:8081/badcode`
- **Method**: `GET`
- **Expected Status**: `404 Not Found`
- **Console Output**:
  ```text
  CACHE MISS
  ```

---

## F. Implementation explained

### Source Files & Responsibilities

| File Path | Class / Interface | Responsibility |
|---|---|---|
| `.../service/urlService.java` | `urlService` | Implements Cache-Aside logic using `RedisTemplate<String, String>`; creates hashes; handles cache read/write. |
| `.../config/RedisConfig.java` | `RedisConfig` | Configures `RedisTemplate<String, String>` bean using `StringRedisSerializer` for both keys and values. |
| `.../config/MongoConfig.java` | `MongoConfig` | Registers `MongoClientSettingsBuilderCustomizer` configuring 2-second connection, socket, and server-selection timeouts. |
| `.../config/WebConfig.java` | `WebConfig` | Implements `WebMvcConfigurer` adding CORS configuration for frontend origins on ports 3000, 5173, 5500. |
| `.../controller/urlController.java` | `urlController` | Exposes `/shorten` and `/{shortCode}` endpoints. |
| `.../repository/urlRepository.java` | `urlRepository` | Spring Data MongoDB repository for CRUD on `urlMapping`. |

### Cache-Aside Mechanism & Accurate Lifecycle

1. **Write Path (`createShortUrl`)**:
   - Computes SHA-256 substring of `longUrl`.
   - Handles collisions via random character append.
   - **Important**: Writes ONLY to MongoDB: `repo.save(mapping)`.
   - **Redis is NOT populated during URL creation**. The cache remains cold until the first read request arrives.

2. **Read Path (`getLongUrl`)**:
   ```java
   String cachedUrl = redisTemplate.opsForValue().get(shortUrl);
   if (cachedUrl != null) {
       System.out.println("CACHE HIT");
       return cachedUrl;
   }

   System.out.println("CACHE MISS");
   Optional<urlMapping> mapping = repo.findByShortUrl(shortUrl);
   if (mapping.isPresent()) {
       String longUrl = mapping.get().getLongUrl();
       redisTemplate.opsForValue().set(shortUrl, longUrl); // Stored in Redis!
       return longUrl;
   }
   return null;
   ```

3. **Cache Key and TTL Inspection**:
   - **Cache Key**: `shortUrl` (the 6-character code, e.g., `"a3e1f0"`).
   - **Cache Value**: `longUrl` (the original destination, e.g., `"https://docs.docker.com"`).
   - > [!IMPORTANT]
     > **No TTL (Time-To-Live) is implemented**: The source code executes `redisTemplate.opsForValue().set(shortUrl, longUrl)` without timeout arguments. Cached entries live permanently in Redis unless manually cleared with `DEL` or evicted by memory policy.

---

## G. Verification and expected results

### 1. Terminal Verification of Cache Hit & Miss
Send requests and inspect application stdout console:
- **PowerShell**:
  ```powershell
  # 1. Shorten
  $res = Invoke-RestMethod -Uri "http://localhost:8081/shorten" -Method Post -ContentType "application/json" -Body '{"longUrl":"https://redis.io"}'
  $code = $res.shortUrl.Split("/")[-1]

  # 2. First call (Triggers CACHE MISS)
  $null = Invoke-WebRequest -Uri "http://localhost:8081/$code" -MaximumRedirection 0 -ErrorAction SilentlyContinue

  # 3. Second call (Triggers CACHE HIT)
  $null = Invoke-WebRequest -Uri "http://localhost:8081/$code" -MaximumRedirection 0 -ErrorAction SilentlyContinue
  ```

### 2. Verify Key in Redis CLI
Verify that Redis holds the key and check remaining TTL:
```bash
docker exec -it url-redis redis-cli GET "<shortCode>"
docker exec -it url-redis redis-cli TTL "<shortCode>"
```
**Expected Output**:
- `GET <shortCode>` returns `"https://redis.io"`.
- `TTL <shortCode>` returns `-1` (confirming **no expiration** is set).

---

## H. JMeter testing (where applicable)

### JMeter Test Plan Setup for Cache-Hit vs Cache-Miss Performance

1. **Create Thread Group**:
   - Threads: `50`
   - Ramp-up: `2`
   - Loop Count: `20` (Total 1,000 requests)
2. **Add HTTP Request Sampler**:
   - Method: `GET`
   - Server: `localhost`
   - Port: `8081`
   - Path: `/<shortCode>` *(use previously created short code)*
   - Uncheck *Follow Redirects* in Advanced settings.
3. **Add Listeners**:
   - `Summary Report`
   - `Response Time Graph`
4. **Metrics to Inspect**:
   - Run 1 (Cold cache / Cache-Miss on first call): Latency includes MongoDB disk lookup.
   - Run 2 (Warm cache / Cache-Hit): Average latency drops significantly to sub-millisecond ranges because MongoDB is completely bypassed and served from in-memory Redis.

---

## I. Common errors and quick fixes

1. **Error**: `RedisConnectionFailureException: Unable to connect to Redis`
   - **Cause**: Redis container is not running on port 6379.
   - **Fix**: Run `docker run -d --name url-redis -p 6379:6379 redis:7-alpine`.
2. **Error**: `MongoSocketOpenException ... Connection refused: localhost:27017`
   - **Cause**: MongoDB container is not running on port 27017.
   - **Fix**: Run `docker run -d --name url-mongo -p 27017:27017 mongo:latest`.
3. **Error**: `Port 8081 already in use`
   - **Cause**: `UrlShortener` or `webcrawler` is currently active on port 8081.
   - **Fix**: Stop the conflicting process.

---

## J. Exam quick revision

### Clean-Start Command Sequence
```powershell
# 1. Start Containers
docker run -d --name url-redis -p 6379:6379 redis:7-alpine
docker run -d --name url-mongo -p 27017:27017 mongo:latest

# 2. Run Application
cd UrlShortenerRedis
.\mvnw.cmd spring-boot:run

# 3. Create Short URL
Invoke-RestMethod -Uri "http://localhost:8081/shorten" -Method Post -ContentType "application/json" -Body '{"longUrl":"https://docker.com"}'
```

### 3 Key Files / Methods to Remember
1. `urlService.java`: `getLongUrl()` (Cache-Aside pattern: checks Redis, falls back to Mongo, writes to Redis).
2. `RedisConfig.java`: Definition of `RedisTemplate<String, String>` bean.
3. `MongoConfig.java`: 2-second timeout configuration.

### Architectural Differences vs Non-Redis Version (`UrlShortener`)

| Feature | `UrlShortener` | `UrlShortenerRedis` |
|---|---|---|
| Caching Layer | None (Pure MongoDB) | Redis 7 (`RedisTemplate<String, String>`) |
| Read Path (`GET /{code}`) | Hits MongoDB every request | Checks Redis first; MongoDB fallback on miss |
| Write Path (`POST /shorten`) | MongoDB only | MongoDB only (Lazy loading on first read) |
| Timeout Config | Default Spring timeouts | Custom 2s timeouts in `MongoConfig.java` |
| CORS Support | None | Configured in `WebConfig.java` |

### Likely Viva Questions & Answers
- **Q1: Why is the cache populated during GET (read) rather than POST (write)?**
  *A*: This implements the Cache-Aside / Lazy Loading pattern. Only URLs that are actually requested are placed in memory, preventing cache pollution by URLs that are generated but never clicked.
- **Q2: Does this implementation set a TTL on cached URLs?**
  *A*: No. The code calls `redisTemplate.opsForValue().set(shortUrl, longUrl)` without duration parameters, so entries persist indefinitely unless evicted by Redis memory policies.
- **Q3: What happens if Redis fails?**
  *A*: In the current code, `opsForValue().get()` would throw a `RedisConnectionFailureException`. (In production systems, this is typically wrapped in a try/catch to gracefully fall back to MongoDB).

### Cleanup Commands
```powershell
docker stop url-redis url-mongo; docker rm url-redis url-mongo
```

---

## Known Issues / Setup Blockers

1. **Missing Docker Compose Configuration**:
   - *Location*: Workspace folder `UrlShortenerRedis`.
   - *Exam Impact*: No `docker-compose.yml` is provided. The user must manually run two Docker commands (`docker run ... redis` and `docker run ... mongo`) to bring up required infrastructure before starting the app.
2. **Missing Initial README**:
   - *Location*: The project folder had no `README.md` at all.
3. **No Uncaught Redis Fallback (Hard Dependency on Redis)**:
   - *Location*: `urlService.java` (lines 49, 63).
   - *Exam Impact*: Unlike `RateLimiter` or `autocomplete-system`, `urlService` does not enclose Redis operations in a try-catch block. If Redis is down, `GET /{shortCode}` immediately throws an unhandled exception and fails rather than degrading gracefully to MongoDB.
4. **Port 8081 Collision**:
   - *Location*: `src/main/resources/application.properties` sets `server.port=8081`.
   - *Exam Impact*: Conflicts with `UrlShortener` and `webcrawler`.
