# Distributed Web Crawler — Redis Frontier with Dynamic Web Content Discovery

> **UCS3513 System Design Laboratory — Lab Exercise 8**  
> *Design and Develop a Web Crawler for Automated Web Content Discovery*

---

## A. Purpose

This project implements a fully dynamic, asynchronous **Distributed Web Crawler** built with **Spring Boot**, **JSoup**, and **Redis**. The primary system design concept demonstrated is the **Web Crawler Architecture with Distributed Visited Tracking, Frontier Queueing, and Domain-Boundary Traversal Control**. 

The system accepts seed URLs dynamically via REST APIs, manages an asynchronous BFS frontier queue, extracts and normalizes hyperlinks across arbitrary DOM depths, enforces configurable crawl budgets (maximum pages and max depth), restricts requests to permitted domains to prevent infinite internet crawling, and leverages Redis for atomic, O(1) URL deduplication across concurrent threads and jobs.

```
 Client ──POST /crawler/start {seedUrl, maxPages, maxDepth, allowedDomain}──▶ CrawlerController
                                                                                    │
                                                                   @Async CrawlerService.crawl()
                                                                                    │
  ┌─────────────────────────────────────────────────────────────────────────────────┴───────────────────────────────────────────────────────┐
  ▼                                                                                                                                         ▼
[Redis Frontier Queue]                                                                                                            [Redis Visited Set]
crawler:job:{id}:queue                                                                                                           crawler:job:{id}:visited
 (List: CrawlTask)                                                                                                                  (Set: Normalized URLs)
  │                                                                                                                                         ▲
  ▼                                                                                                                                         │
dequeue() ──▶ Check Depth <= maxDepth ──▶ Atomic Visit Check (SADD) ──▶ [Already Visited?] ──YES──▶ (Skip)                                 │
                                                  │                                                                                         │
                                                [NO] (Fresh URL) ───────────────────────────────────────────────────────────────────────────┘
                                                  ▼
                                      Politeness Delay (e.g. 50ms)
                                                  ▼
                                      JSoup.connect(url).get()
                                                  │
                                      Extract <a href="..."> tags
                                                  │
                                      UrlValidator.isValid() & normalize()
                                                  │
                                      Check Permitted Domain (allowedDomain)
                                                  │
                                                  ▼
                                      Atomic Discovery Check (SADD)
                                      crawler:job:{id}:discovered
                                                  │
                                        [Freshly Discovered?]
                                         ├── NO  ──▶ Ignore (Duplicate Link)
                                         └── YES ──▶ Check (depth + 1 <= maxDepth)
                                                      └── Enqueue CrawlTask(childUrl, depth + 1)
```

---

## B. Prerequisites and setup

### Required Tools & Versions
- **Java JDK**: 17 or higher (`java -version`)
- **Maven**: 3.6+ or included Maven Wrapper (`mvnw.cmd` / `./mvnw`)
- **Docker Engine & Docker Compose**: CLI runtime (`docker --version`, `docker compose version`)
- **Python 3**: For hosting the local controlled test dataset (`python --version`)
- **Redis**: 7-alpine running via Docker on port `6379`

### Step-by-Step Setup Commands

#### 1. Navigate to Project Directory
- **PowerShell (Windows)**:
  ```powershell
  cd c:\Users\sandh\Documents\sem5\System-design\webcrawler
  ```
- **Ubuntu / Linux**:
  ```bash
  cd webcrawler
  ```

#### 2. Start the Local Controlled Dataset Web Server (Terminal 1)
To ensure the crawler operates in a safe, controlled sandbox without crawling the live internet, serve the included interconnected test pages on port `9000`:
- **PowerShell (Windows)**:
  ```powershell
  python -m http.server 9000 --directory crawler_dataset
  ```
- **Ubuntu / Linux**:
  ```bash
  python3 -m http.server 9000 --directory crawler_dataset
  ```

#### 3. Start Redis (Terminal 2)
- **PowerShell / Ubuntu**:
  ```bash
  docker compose up -d redis
  ```
- Verify Redis is responding:
  ```bash
  docker exec -it webcrawler-redis redis-cli ping
  ```
  *(Expected: `PONG`)*

---

## C. Docker setup (terminal only)

The project includes a multi-stage `Dockerfile` and `docker-compose.yml` orchestrating Redis and the crawler service.

### Docker Engine Verification
- **PowerShell**:
  ```powershell
  docker version
  docker ps
  ```
- **Ubuntu / Linux**:
  ```bash
  docker version
  docker ps
  ```

### Services Defined in `docker-compose.yml`

| Service Name | Container Name | Image / Build | Port Mapping | Healthcheck / Dependency |
|---|---|---|---|---|
| `redis` | `webcrawler-redis` | `redis:7-alpine` | `6379:6379` | `redis-cli ping` (interval: 5s) |
| `webcrawler` | `webcrawler-app` | Built from `Dockerfile` | `8081:8081` | Depends on healthy redis |

### Container Lifecycle Commands
- **Build and start both services**:
  ```bash
  docker compose up --build -d
  ```
- **Inspect application container logs**:
  ```bash
  docker compose logs -f webcrawler
  ```
- **Inspect Redis keys inside container**:
  ```bash
  docker exec -it webcrawler-redis redis-cli KEYS "crawler:*"
  docker exec -it webcrawler-redis redis-cli SMEMBERS "crawler:jobs"
  ```
- **Stop services**:
  ```bash
  docker compose stop
  ```
- **Tear down containers and volumes**:
  ```bash
  docker compose down -v
  ```

---

## D. Run the application

### Option 1: Native Execution with Maven Wrapper (Recommended for Local Testing)
Ensure Redis is running (`docker compose up -d redis`).

- **PowerShell (Windows)**:
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```
- **Ubuntu / Linux**:
  ```bash
  ./mvnw spring-boot:run
  ```

### Option 2: Run via Docker Compose
When running inside Docker, the crawler accesses host-hosted test datasets using `http://host.docker.internal:9000/page1.html`.
```bash
docker compose up --build -d
```

### Configuration Parameters (`src/main/resources/application.properties`)
- `server.port=8081`
- `spring.data.redis.host=${SPRING_DATA_REDIS_HOST:localhost}`
- `spring.data.redis.port=${SPRING_DATA_REDIS_PORT:6379}`

### Expected Startup Output
```text
Tomcat started on port 8081 (http) with context path '/'
Started WebcrawlerApplication in X.XXX seconds
```

### Verify Service Health
- **PowerShell / Bash**:
  ```bash
  curl http://localhost:8081/crawler/health
  ```
  *Expected Output*:
  ```json
  {"status":"UP","redisConnected":true,"activeJobs":0,"latestJobId":"default"}
  ```

---

## E. REST API documentation & testing

### API Endpoints Summary

| HTTP Method | Full Local URL | Purpose | Request Body / Query Params | Expected Status |
|---|---|---|---|---|
| `POST` | `http://localhost:8081/crawler/start` | Start asynchronous crawl job | JSON body or query params: `seedUrl`, `jobId`, `maxPages`, `maxDepth`, `allowedDomain` | `202 Accepted` |
| `GET` | `http://localhost:8081/crawler/status` | Get status of latest or specified job | Query param: `jobId` (optional) | `200 OK` |
| `GET` | `http://localhost:8081/crawler/jobs/{jobId}` | Get comprehensive job metrics & timings | Path variable: `jobId` | `200 OK` |
| `GET` | `http://localhost:8081/crawler/jobs` | List all registered crawl jobs | None | `200 OK` |
| `GET` | `http://localhost:8081/crawler/visited` | Paginated list of visited URLs | `jobId`, `page`, `size` | `200 OK` |
| `GET` | `http://localhost:8081/crawler/discovered` | Paginated list of discovered URLs | `jobId`, `page`, `size` | `200 OK` |
| `GET` | `http://localhost:8081/crawler/queue` | Inspect pending frontier queue URLs | `jobId`, `page`, `size` | `200 OK` |
| `GET` | `http://localhost:8081/crawler/failed` | View inaccessible URLs & error reasons | `jobId` | `200 OK` |
| `DELETE` | `http://localhost:8081/crawler/jobs/{jobId}/state` | Clear Redis state for a specific job | Path variable: `jobId` | `200 OK` |
| `GET` | `http://localhost:8081/crawler/health` | Service health & Redis check | None | `200 OK` |

### Copy-Pasteable Postman / Curl Examples

#### 1. Start Crawl with Custom Limits (JSON Body)
- **Method**: `POST`
- **URL**: `http://localhost:8081/crawler/start`
- **Header**: `Content-Type: application/json`
- **Body**:
  ```json
  {
    "seedUrl": "http://localhost:9000/page1.html",
    "jobId": "exam-demo-job",
    "maxPages": 25,
    "maxDepth": 3,
    "allowedDomain": "localhost",
    "stayOnDomain": true,
    "requestTimeoutMs": 3000,
    "politenessDelayMs": 50
  }
  ```
- **Expected Status**: `202 Accepted`
- **Expected Response (JSON)**:
  ```json
  {
    "jobId": "exam-demo-job",
    "seedUrl": "http://localhost:9000/page1.html",
    "status": "RUNNING",
    "maxPages": 25,
    "maxDepth": 3,
    "allowedDomain": "localhost",
    "pagesVisited": 0,
    "pagesDiscovered": 0,
    "pagesFailed": 0,
    "queueSize": 0
  }
  ```

#### 2. Inspect Job Progress & Results
- **URL**: `http://localhost:8081/crawler/jobs/exam-demo-job`
- **Method**: `GET`
- **Expected Response**:
  ```json
  {
    "jobId": "exam-demo-job",
    "seedUrl": "http://localhost:9000/page1.html",
    "status": "COMPLETED",
    "maxPages": 25,
    "maxDepth": 3,
    "allowedDomain": "localhost",
    "pagesVisited": 7,
    "pagesDiscovered": 8,
    "pagesFailed": 1,
    "queueSize": 0,
    "durationMs": 420,
    "message": "Crawled 7 pages, discovered 8 URLs"
  }
  ```

#### 3. Inspect Visited URLs with Pagination
- **URL**: `http://localhost:8081/crawler/visited?jobId=exam-demo-job&page=0&size=5`
- **Method**: `GET`
- **Expected Response**:
  ```json
  {
    "items": [
      "http://localhost:9000/page1.html",
      "http://localhost:9000/page2.html",
      "http://localhost:9000/page3.html",
      "http://localhost:9000/page4.html",
      "http://localhost:9000/section/page5.html"
    ],
    "page": 0,
    "size": 5,
    "totalItems": 7,
    "totalPages": 2
  }
  ```

#### 4. Inspect Inaccessible / Failed URLs
- **URL**: `http://localhost:8081/crawler/failed?jobId=exam-demo-job`
- **Method**: `GET`
- **Expected Response**:
  ```json
  {
    "jobId": "exam-demo-job",
    "failedCount": 1,
    "failures": {
      "http://localhost:9000/does-not-exist-404.html": "HTTP 404"
    }
  }
  ```

---

## F. Implementation explained

### Source Files & Architecture

| File Path | Component | Responsibility |
|---|---|---|
| `.../controller/CrawlerController.java` | REST Controller | Exposes endpoints for job lifecycle, query pagination, frontier queue inspection, and health check. |
| `.../service/CrawlerService.java` | Async Crawl Engine | Orchestrates the BFS traversal loop; interacts with JSoup; enforces `maxPages`, `maxDepth`, and domain restrictions. |
| `.../service/RedisCrawlStateService.java` | Redis State Manager | Manages Redis Keys (`visited`, `discovered`, `queue`, `failed`, `meta`) isolated by `jobId` with atomic Redis operations and memory fallback. |
| `.../service/UrlValidator.java` | Validation & Normalizer | Strips hash fragments (`#...`), normalizes path dots (`..`), filters media assets (`.png`, `.pdf`), validates HTTP/HTTPS schemes, and checks domain restrictions. |
| `.../model/CrawlRequest.java` | Request DTO | Configurable parameters: seed URL, max pages, max depth, allowed domain, timeouts, and politeness delay. |
| `.../model/CrawlTask.java` | Queue Task | Encapsulates a URL waiting to be fetched along with its crawl depth and parent provenance. |
| `.../model/CrawlJobDetails.java` | Job Metrics DTO | Real-time counts of visited, discovered, failed pages, queue length, timestamps, and duration. |

### Core Algorithmic Workflow

1. **Job Initialization**:
   - Seed URL is validated and normalized (`UrlValidator.normalize()`).
   - If `allowedDomain` is omitted and `stayOnDomain` is true, the domain is automatically locked to the seed host (`localhost`).
   - Seed URL is marked discovered atomically and enqueued into `crawler:job:{id}:queue` as `CrawlTask(seed, depth=0)`.

2. **Breadth-First Search (BFS) Frontier Traversal**:
   - `dequeue()` pops the oldest task from the Redis List (`LPOP`).
   - If task depth $> \text{maxDepth}$, the page is dropped.
   - Atomically attempts to insert URL into Redis Set `crawler:job:{id}:visited` (`SADD`). If SADD returns 0, the page was already visited by another worker and is skipped.
   - A politeness delay is observed (e.g. 50ms) to prevent server hammering.
   - JSoup downloads the document: `Jsoup.connect(url).timeout(...).get()`.

3. **Link Extraction, Normalization & Filtering**:
   - Evaluates all `a[href]` tags with `link.absUrl("href")`.
   - Filters out non-HTTP schemes (`mailto:`, `ftp:`, `javascript:`).
   - Normalizes path and removes fragments (e.g., `page2.html#section` becomes `page2.html`).
   - Filters out static binaries (`.jpg`, `.pdf`, `.zip`).
   - Enforces domain boundary: ensures URL host matches `allowedDomain`.
   - Performs atomic discovery check: `SADD crawler:job:{id}:discovered`.
   - If newly discovered AND $(\text{depth} + 1) \le \text{maxDepth}$, enqueues `CrawlTask(child, depth + 1)`.

4. **Termination Conditions**:
   - Frontier queue is completely empty (`LPOP` returns null), OR
   - Visited count reaches `maxPages`, OR
   - Job is cancelled/stopped via API.

---

## G. Dynamic test dataset guide

The project includes an interconnected test site in `webcrawler/crawler_dataset/`:

```
crawler_dataset/
├── page1.html                  # Seed (Depth 0): Links to Page 2, Page 3, external domain, error demo
├── page2.html                  # Depth 1: Links to Page 4, section/page5.html, cycle back to Page 1
├── page3.html                  # Depth 1: Links to Page 4, section/page5.html
├── page4.html                  # Depth 2: Links to section/page6.html, back to Page 2
├── error-demo.html             # Edge cases: Links to 404 nonexistent page, malformed URL, ftp
└── section/
    ├── page5.html              # Depth 2: Relative links (../page2.html)
    ├── page6.html              # Depth 3: Deep link to page 7
    └── page7.html              # Depth 4: Terminal page for maxDepth verification
```

### Starting the Local Dataset Server
- **PowerShell**:
  ```powershell
  python -m http.server 9000 --directory webcrawler\crawler_dataset
  ```
- **Ubuntu / Linux**:
  ```bash
  python3 -m http.server 9000 --directory webcrawler/crawler_dataset
  ```

---

## H. JMeter performance testing

The project includes a ready-to-run JMeter performance plan: `jmeter/webcrawler_test_plan.jmx`.

### Running in Non-GUI (CLI) Mode

- **PowerShell (Windows)**:
  ```powershell
  cd jmeter
  jmeter -n -t webcrawler_test_plan.jmx `
    -Jhost=localhost -Jport=8081 -Jthreads=25 -Jrampup=5 -Jloops=10 `
    -l results.jtl `
    -e -o dashboard-report
  ```

- **Ubuntu / Linux**:
  ```bash
  cd jmeter
  jmeter -n -t webcrawler_test_plan.jmx \
    -Jhost=localhost -Jport=8081 -Jthreads=25 -Jrampup=5 -Jloops=10 \
    -l results.jtl \
    -e -o dashboard-report
  ```

### Parameters to Vary for Lab Evaluation:
- `-Jthreads=1` : Baseline single-user response latency.
- `-Jthreads=10` : Moderate concurrency.
- `-Jthreads=25` : Heavy concurrent queries.
- `-Jthreads=50` : Stress test verifying Redis Set and List lookup throughput.

### Metrics to Inspect in `dashboard-report/index.html`:
- **Throughput (Requests/sec)**: Rate of queries handled across Redis sets.
- **Average Response Time**: Sub-millisecond latency for cached Redis status checks.
- **Error Percentage**: Must be `0.00%` (asserting HTTP 200 on all queries).

---

## I. Common errors and quick fixes

1. **Error**: `Connection refused: localhost:6379`
   - **Cause**: Redis container is not running.
   - **Fix**: Run `docker compose up -d redis`.
2. **Error**: `Seed page could not be reached / status FAILED`
   - **Cause**: Python test dataset server on port 9000 is not running.
   - **Fix**: Run `python -m http.server 9000 --directory crawler_dataset` in a separate terminal.
3. **Error**: `409 Conflict: Crawl job <id> is already running`
   - **Cause**: Triggered another start request with an identical active `jobId`.
   - **Fix**: Wait for the job to finish, use a different `jobId`, or clear state via `DELETE /crawler/jobs/{jobId}/state`.
4. **Error**: `Crawler runs indefinitely`
   - **Cause**: Seed URL pointed to an external site with infinite links.
   - **Fix**: Always specify `allowedDomain` or test with `http://localhost:9000/page1.html`.

---

## J. Exam quick revision

### Clean-Start Command Sequence (Exam Order)
```powershell
# 1. Start Redis
docker compose up -d redis

# 2. Start Test Website (in separate terminal)
python -m http.server 9000 --directory crawler_dataset

# 3. Start Web Crawler
.\mvnw.cmd spring-boot:run

# 4. Trigger Crawl
Invoke-RestMethod -Uri "http://localhost:8081/crawler/start?seedUrl=http://localhost:9000/page1.html" -Method Post

# 5. Check Status
Invoke-RestMethod -Uri "http://localhost:8081/crawler/status"
```

### 3 Key Source Files to Remember
1. `CrawlerService.java`: BFS crawl loop over `dequeue()`, JSoup link extraction, and depth checks.
2. `RedisCrawlStateService.java`: Atomic `SADD` on `crawler:visited` and `crawler:discovered`.
3. `UrlValidator.java`: Normalization (fragment stripping) and domain matching.

### Likely Viva Questions & Answers
- **Q1: How do you prevent circular link crawling (infinite loops)?**
  *A*: Before crawling any page, the crawler checks and inserts the URL into a Redis Set (`SADD`). If SADD returns 0, the URL is already visited and skipped immediately.
- **Q2: Why use Redis for the URL queue and visited set?**
  *A*: Redis allows multiple crawler workers across different machines to share the frontier queue and deduplicate visited URLs in $O(1)$ time without duplicate crawls.
- **Q3: What is URL normalization and why is it essential?**
  *A*: Normalization strips anchor fragments (`#sec`), converts schemes and hosts to lowercase, and resolves relative path segments so that equivalent URLs (e.g., `page.html` and `page.html#top`) are not crawled as separate pages.

### Cleanup Commands
```powershell
docker compose down -v
```
