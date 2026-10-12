# Autocomplete Search System — Trie with Redis Cache-Aside

## A. Purpose

This project implements a low-latency **Autocomplete Search Service** built with Spring Boot, combining an in-memory **Trie (Prefix Tree)** with a **Redis Cache-Aside Layer**. The primary system design concept demonstrated is **Prefix-Matching Query Expansion with Multi-Tiered In-Memory Caching**. Search queries are matched against a loaded frequency dataset; repeated prefix requests are answered in sub-millisecond time directly from Redis, while cache misses traverse the Trie, rank candidate terms by popularity frequency, and write top-$K$ suggestions back to Redis with a 300-second TTL.

```
 Client ──GET /api/autocomplete?prefix=app&limit=10──▶ Spring Boot Controller
                                                           │
                                           1. Check Redis (autocomplete:app:10)
                                                           │
                                      HIT ◀────────────────┴────────────────▶ MISS
                                       │                                        │
                               Return cached list                   2. DFS the Trie subtree
                                 (Source: CACHE)                        for "app", sort by
                                                                        frequency, take top-K
                                                                               │
                                                                    3. Store result in Redis
                                                                       (TTL = 300s) and return
                                                                         (Source: TRIE)
```

---

## B. Prerequisites and setup

### Required Tools & Versions
- **Java JDK**: 17 or higher (`java -version`)
- **Maven**: 3.6+ installed on system (`mvn -version`) *(Note: No `mvnw` wrapper file is included in this folder)*
- **Docker Engine & Docker Compose**: CLI runtime (`docker compose version`)

### Step-by-Step Setup Commands

#### 1. Navigate to Project Directory
- **PowerShell (Windows)**:
  ```powershell
  cd c:\Users\sandh\Documents\sem5\System-design\autocomplete-system
  ```
- **Bash (Linux/macOS)**:
  ```bash
  cd autocomplete-system
  ```

#### 2. Start Services via Docker Compose
The project provides a self-contained `docker-compose.yml` that builds the application image and starts a dedicated Redis container:
- **PowerShell / Bash**:
  ```bash
  docker compose up --build -d
  ```

#### 3. Verify Containers are Healthy & Running
- **PowerShell / Bash**:
  ```bash
  docker compose ps
  ```
  Expected containers: `autocomplete-redis` and `autocomplete-app`.

---

## C. Docker setup (terminal only)

### Docker Engine Verification
- **PowerShell**:
  ```powershell
  docker version
  docker ps
  ```

### Container Setup & Lifecycle

| Service | Container Name | Image / Build | Port Mapping | Healthcheck |
|---|---|---|---|---|
| `redis` | `autocomplete-redis` | `redis:7-alpine` | Internal (Bridge) | `redis-cli ping` |
| `app` | `autocomplete-app` | Built from `Dockerfile` | `8080:8080` | Depends on healthy redis |

### Terminal Inspection Commands
- **Check application startup logs**:
  ```bash
  docker compose logs -f app
  ```
  *(Look for: `Dataset loaded into Trie: 99 terms from classpath:data/search_terms.csv`)*
- **Inspect Redis cache keys directly**:
  ```bash
  docker exec -it autocomplete-redis redis-cli KEYS "autocomplete:*"
  docker exec -it autocomplete-redis redis-cli GET "autocomplete:app:10"
  docker exec -it autocomplete-redis redis-cli TTL "autocomplete:app:10"
  ```
- **Flush Redis cache for clean benchmark testing**:
  ```bash
  docker exec -it autocomplete-redis redis-cli FLUSHALL
  ```
- **Tear down containers**:
  ```bash
  docker compose down
  ```

---

## D. Run the application

### Option 1: Run via Docker Compose (Recommended)
```bash
docker compose up -d
```

### Option 2: Run Locally with Native Maven (Requires Local Redis on 6379)
If running Redis locally or via standalone container:
- **PowerShell (Windows)**:
  ```powershell
  mvn clean package -DskipTests
  $env:REDIS_HOST="localhost"; $env:REDIS_PORT="6379"
  java -jar target/autocomplete-system.jar
  ```
- **Bash (Linux)**:
  ```bash
  mvn clean package -DskipTests
  REDIS_HOST=localhost REDIS_PORT=6379 java -jar target/autocomplete-system.jar
  ```

### Startup Verification
- **Default Port & Base URL**: `http://localhost:8080`
- **Health Check**:
  ```bash
  curl http://localhost:8080/api/health
  ```
  *Expected Output*:
  ```json
  {"status":"UP","trieSize":99,"cacheHits":0,"cacheMisses":0,"cacheErrors":0,"cacheTtlSeconds":300}
  ```

### Stop the Application
- If running in foreground: `Ctrl + C`
- If running via Docker Compose: `docker compose down`

---

## E. API testing with Postman (where applicable)

The project includes an existing Postman collection: `postman/Autocomplete.postman_collection.json`.

### Implemented Endpoints

| HTTP Method | Full Local URL | Required Headers | Query Params | Expected Status | Purpose |
|---|---|---|---|---|---|
| `GET` | `http://localhost:8080/api/autocomplete?prefix=app&limit=10` | None | `prefix=app`, `limit=10` | `200 OK` | Fetch autocomplete suggestions (1st call: `TRIE`, 2nd call: `CACHE`) |
| `GET` | `http://localhost:8080/api/health` | None | None | `200 OK` | Inspect trie size and cache hit/miss statistics |
| `GET` | `http://localhost:8080/actuator/health` | None | None | `200 OK` | Spring Boot Actuator health check |
| `GET` | `http://localhost:8080/api/autocomplete?prefix=&limit=10` | None | `prefix=` | `400 Bad Request` | Validate that prefix must not be empty |

### Copy-Pasteable Postman Test Cases

#### 1. Success Case 1: First Query (Cache Miss -> Trie Traversal)
- **URL**: `http://localhost:8080/api/autocomplete?prefix=app&limit=5`
- **Method**: `GET`
- **Expected Status**: `200 OK`
- **Expected Response (JSON)**:
  ```json
  {
    "prefix": "app",
    "count": 5,
    "source": "TRIE",
    "tookMillis": 2,
    "suggestions": [
      { "term": "apple", "frequency": 9500 },
      { "term": "app store", "frequency": 8900 },
      { "term": "application form", "frequency": 7200 },
      { "term": "appointment", "frequency": 6100 },
      { "term": "application software", "frequency": 5400 }
    ]
  }
  ```

#### 2. Success Case 2: Immediate Repeated Query (Cache Hit)
- **URL**: `http://localhost:8080/api/autocomplete?prefix=app&limit=5`
- **Method**: `GET`
- **Expected Status**: `200 OK`
- **Expected Response (JSON)**:
  Identical suggestions list, but `"source": "CACHE"` and `"tookMillis": 0` or `1`.

#### 3. Failure / Validation Case: Missing or Empty Prefix
- **URL**: `http://localhost:8080/api/autocomplete?prefix=&limit=10`
- **Method**: `GET`
- **Expected Status**: `400 Bad Request`
- **Expected Response (JSON)**:
  ```json
  {
    "timestamp": "...",
    "status": 400,
    "error": "Bad Request",
    "message": "prefix must not be empty",
    "path": "/api/autocomplete"
  }
  ```

---

## F. Implementation explained

### Source Files & Responsibilities

| File Path | Class / Interface | Responsibility |
|---|---|---|
| `.../trie/Trie.java` | `Trie` | Prefix tree implementation. `insert()` adds characters and accumulates frequency; `searchByPrefix()` walks prefix path, DFS-collects candidates, and sorts by frequency descending. |
| `.../trie/TrieNode.java` | `TrieNode` | Node storing `Map<Character, TrieNode> children`, `boolean isEndOfWord`, and `long frequency`. |
| `.../service/AutocompleteService.java` | `AutocompleteService` | Cache-Aside layer. Checks Redis key `autocomplete:<prefix>:<limit>`. On hit returns `Source.CACHE`. On miss searches Trie, stores in Redis with 300s TTL, returns `Source.TRIE`. Gracefully degrades to Trie if Redis fails. |
| `.../controller/AutocompleteController.java` | `AutocompleteController` | Exposes `/api/autocomplete` and `/api/health`. Validates input parameters. |
| `.../loader/DataLoader.java` | `DataLoader` | Implements `ApplicationRunner`; parses `src/main/resources/data/search_terms.csv` at startup and seeds the Trie. |
| `.../config/RedisConfig.java` | `RedisConfig` | Configures `RedisTemplate<String, List<SearchTerm>>` with Jackson JSON serializers. |

### Core Trie Search Algorithm

1. **Navigate to Prefix Node**:
   ```java
   TrieNode current = root;
   for (char c : normalized.toCharArray()) {
       current = current.childFor(c);
       if (current == null) return Collections.emptyList(); // Prefix not in dataset
   }
   ```
2. **Subtree DFS Collection (`collect`)**:
   Recursively gathers every node beneath the prefix node where `isEndOfWord == true`, rebuilding the full word using a `StringBuilder` buffer.
3. **Popularity Ranking & Top-K Truncation**:
   ```java
   matches.sort(Comparator.comparingLong(SearchTerm::getFrequency).reversed()
           .thenComparing(SearchTerm::getTerm));
   return matches.size() > limit ? matches.subList(0, limit) : matches;
   ```
4. **Time Complexities**:
   - Prefix Walk: $O(L)$ where $L$ is prefix length.
   - Candidate Search: $O(M)$ where $M$ is total nodes in the subtree.
   - Ranking: $O(C \log C)$ where $C$ is matching words count.
   - Redis Cache Hit: $O(1)$ independent of dataset size.

---

## G. Verification and expected results

### 1. Functional Verification via Terminal
- **PowerShell**:
  ```powershell
  # First request -> expect TRIE
  (Invoke-RestMethod -Uri "http://localhost:8080/api/autocomplete?prefix=data&limit=3").source
  # Second request -> expect CACHE
  (Invoke-RestMethod -Uri "http://localhost:8080/api/autocomplete?prefix=data&limit=3").source
  ```
- **Bash / Curl**:
  ```bash
  curl -s "http://localhost:8080/api/autocomplete?prefix=data&limit=3" | grep "source"
  curl -s "http://localhost:8080/api/autocomplete?prefix=data&limit=3" | grep "source"
  ```
**Expected Output**: First invocation outputs `"source":"TRIE"`; second invocation outputs `"source":"CACHE"`.

### 2. Verify Health Counters
```bash
curl http://localhost:8080/api/health
```
**Expected Output**: `cacheHits` and `cacheMisses` counters increment accordingly.

---

## H. JMeter testing (where applicable)

The project includes an existing JMeter test plan: `jmeter/autocomplete_test_plan.jmx` and prefix data file `jmeter/prefixes.csv`.

### Running JMeter in CLI (Non-GUI) Mode
From the `autocomplete-system/jmeter` directory:

- **PowerShell / Bash**:
  ```bash
  cd jmeter
  jmeter -n -t autocomplete_test_plan.jmx \
    -Jhost=localhost -Jport=8080 -Jthreads=50 -Jrampup=5 -Jloops=20 \
    -l results.jtl \
    -e -o dashboard-report
  ```

### What this Test Plan Does:
1. Spawns a Thread Group of **50 concurrent virtual users**.
2. Cycles through the 20 search prefixes defined in `prefixes.csv` (e.g., `app`, `data`, `sys`, `mac`, `win`).
3. Sends `GET /api/autocomplete?prefix=${prefix}&limit=10`.
4. Asserts HTTP 200 on all requests.
5. Generates an HTML dashboard at `dashboard-report/index.html`.

### Metrics to Inspect:
- **Cold run vs. Warm run comparison**:
  On a cold run (flush Redis first), Trie DFS executes. On repeat runs, latency drops dramatically to sub-millisecond level as Redis serves requests.
- **Error rate**: Must be `0.00%`.

---

## I. Common errors and quick fixes

1. **Error**: `.\mvnw.cmd : The term '.\mvnw.cmd' is not recognized`
   - **Cause**: Unlike other folders, `autocomplete-system` does not include Maven Wrapper (`mvnw`).
   - **Fix**: Use system Maven (`mvn clean package`) or use Docker (`docker compose up -d`).
2. **Error**: `Port 8080 already in use`
   - **Cause**: `RateLimiter` or `ConsistentHashing` is occupying port 8080.
   - **Fix**: Terminate the other process before running `autocomplete-system`.
3. **Error**: `Trie is empty / trieSize: 0`
   - **Cause**: Dataset file `search_terms.csv` not found on classpath.
   - **Fix**: Verify `src/main/resources/data/search_terms.csv` is present; `DataLoader` logs `Dataset loaded into Trie: 99 terms`.

---

## J. Exam quick revision

### Clean-Start Command Sequence
```powershell
# 1. Start application and Redis using Compose
docker compose up -d --build

# 2. Check health
curl http://localhost:8080/api/health

# 3. Test autocomplete
curl "http://localhost:8080/api/autocomplete?prefix=app&limit=5"
```

### 3 Key Files / Methods to Remember
1. `Trie.java`: `insert(word, frequency)` and `searchByPrefix(prefix, limit)`.
2. `AutocompleteService.java`: `suggest()` (Cache-Aside logic with Redis TTL and fault fallback).
3. `DataLoader.java`: Loads CSV records at startup via `ApplicationRunner`.

### Likely Viva Questions & Answers
- **Q1: What are the advantages of a Trie over linear string search ($O(N)$)?**
  *A*: A Trie locates prefix matches in $O(L + M)$ time (where $L$ is prefix length and $M$ is matching subtree nodes), completely independent of total dataset size $N$. Linear scan takes $O(N \times L)$, which degrades severely with millions of terms.
- **Q2: Why include limit in the Redis cache key (`autocomplete:<prefix>:<limit>`)?**
  *A*: A request for top-5 results cannot satisfy a request for top-10 results. Storing limit in the key prevents partial cache corruption.
- **Q3: What happens if Redis crashes?**
  *A*: `AutocompleteService` catches Redis connection exceptions, logs a warning, and falls back directly to the in-memory Trie. The system experiences higher latency but zero downtime.

### Cleanup Commands
```powershell
docker compose down
```

---

## Known Issues / Setup Blockers

1. **Missing Maven Wrapper (`mvnw` / `mvnw.cmd`)**:
   - *Location*: Root of `autocomplete-system/`.
   - *Exam Impact*: Executing `.\mvnw.cmd` or `./mvnw` will fail with file not found. If running outside Docker, the user must use installed system Maven (`mvn`).
2. **Port 8080 Collision Potential**:
   - *Location*: `src/main/resources/application.yml` defaults to port 8080.
   - *Exam Impact*: Conflicts with `RateLimiter` and `ConsistentHashing`. Stop other 8080 applications before starting this service.
