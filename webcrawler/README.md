# Distributed Web Crawler — Redis Frontier with Asynchronous JSoup Traversal

## A. Purpose

This project implements an **Asynchronous Web Crawler** built with Spring Boot, JSoup, and Redis. The primary system design concept demonstrated is the **Web Crawler Architecture with Distributed Visited Tracking and Frontier Queueing**. When provided with a seed URL, the application asynchronously downloads HTML pages, parses hyperlinked `<a>` tags via JSoup, filters valid web URIs, checks against a distributed Redis Set to prevent cycles and duplicate crawling, and manages crawl states (`IDLE`, `RUNNING`, `COMPLETED`, `FAILED`) in Redis.

```
 Client ──POST /crawler/start?seedUrl=...──▶ CrawlerController
                                                   │
                                     @Async CrawlerService.crawl()
                                                   │
                ┌──────────────────────────────────┴──────────────────────────────────┐
                ▼                                                                     ▼
      UrlQueue (In-Memory Queue)                                       VisitedUrlService (Redis Set)
     [seedUrl, link1, link2, ...]                                           crawler:visited
                │                                                                     ▲
                ▼                                                                     │
        getNext() ──▶ Is Valid? ──▶ Already Visited? ──[No]──▶ Mark Visited in Redis ─┘
                           │                 │
                         [Yes]             [Yes] (Skip)
                           ▼
                 JSoup.connect(url).get()
                           │
             Extract absolute <a href="..."> links
                           │
                 UrlValidator.isValid()
                           │
          Mark Discovered in Redis (crawler:discovered)
                           │
                  Add to UrlQueue
```

---

## B. Prerequisites and setup

### Required Tools & Versions
- **Java JDK**: 17 or higher (`java -version`)
- **Maven**: 3.6+ or included Maven Wrapper (`mvnw.cmd` / `./mvnw`)
- **Python 3** *(for serving the safe local test dataset)*: `python --version`
- **Docker Engine**: CLI runtime for Redis container (`docker --version`)

### Step-by-Step Setup Commands

#### 1. Navigate to Project Directory
- **PowerShell (Windows)**:
  ```powershell
  cd c:\Users\sandh\Documents\sem5\System-design\webcrawler
  ```
- **Bash (Linux/macOS)**:
  ```bash
  cd webcrawler
  ```

#### 2. Start Redis Service
The project provides a `docker-compose.yml` for Redis. To start just the Redis container:
- **PowerShell / Bash**:
  ```bash
  docker compose up -d redis
  ```

#### 3. Start the Local Controlled Web Server (Crucial for Safe Testing)
To avoid recursively crawling the live internet, serve the sample dataset provided in `crawler_dataset/`:
- **PowerShell (Windows)**:
  ```powershell
  # Run in a separate terminal from workspace root:
  python -m http.server 9000 --directory webcrawler\crawler_dataset
  ```
- **Bash (Linux/macOS)**:
  ```bash
  python3 -m http.server 9000 --directory webcrawler/crawler_dataset
  ```

---

## C. Docker setup (terminal only)

### Docker Engine Verification
- **PowerShell**:
  ```powershell
  docker version
  docker ps
  ```

### Inspecting Docker Services in `docker-compose.yml`
`docker-compose.yml` defines two services:
- `redis`: container `webcrawler-redis`, port `6379:6379`.
- `webcrawler`: container `webcrawler-app`, port `8081:8081`.

> [!WARNING]
> **Docker Build Pre-requisite**: The `Dockerfile` copies `target/webcrawler-0.0.1-SNAPSHOT.jar`. It is not a multi-stage build. You **must** build the JAR locally first (`mvn clean package -DskipTests`) before running `docker compose up --build`. Otherwise Docker build will fail with file not found.

### Operational Commands
- **Check Redis connection**:
  ```bash
  docker exec -it webcrawler-redis redis-cli ping
  ```
- **Inspect crawler keys in Redis**:
  ```bash
  docker exec -it webcrawler-redis redis-cli KEYS "crawler:*"
  docker exec -it webcrawler-redis redis-cli SMEMBERS "crawler:visited"
  docker exec -it webcrawler-redis redis-cli SMEMBERS "crawler:discovered"
  docker exec -it webcrawler-redis redis-cli GET "crawler:status"
  ```
- **Tear down Redis**:
  ```bash
  docker compose down
  ```

---

## D. Run the application

- **Working Directory**: `webcrawler/`
- **Entry Point**: `com.example.webcrawler.WebcrawlerApplication`
- **Configuration File**: `src/main/resources/application.properties`
  - `server.port=8081`
  - `spring.data.redis.host=${SPRING_DATA_REDIS_HOST:localhost}`
  - `spring.data.redis.port=${SPRING_DATA_REDIS_PORT:6379}`
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
Started WebcrawlerApplication in X.XXX seconds
```

### Stop the Application
Press `Ctrl + C` in the running terminal.

---

## E. API testing with Postman (where applicable)

### Implemented Endpoints

| HTTP Method | Full Local URL | Required Headers | Query Params | Expected Status | Purpose |
|---|---|---|---|---|---|
| `POST` | `http://localhost:8081/crawler/start?seedUrl=http://localhost:9000/page1.html` | None | `seedUrl` | `200 OK` | Starts background async crawl |
| `GET` | `http://localhost:8081/crawler/status` | None | None | `200 OK` | Returns current status (`IDLE`, `RUNNING`, `COMPLETED`, `FAILED`) |
| `GET` | `http://localhost:8081/crawler/visited` | None | None | `200 OK` | Returns JSON array of all visited URLs |
| `GET` | `http://localhost:8081/crawler/discovered` | None | None | `200 OK` | Returns JSON array of all discovered URLs |
| `DELETE` | `http://localhost:8081/crawler/visited` | None | None | `200 OK` | Clears visited URL set in Redis |
| `DELETE` | `http://localhost:8081/crawler/state` | None | None | `200 OK` | Resets all Redis state (visited, discovered, status) |

### Copy-Pasteable Postman Test Cases

#### 1. Success Case: Start Crawling Seed URL
- **URL**: `http://localhost:8081/crawler/start?seedUrl=http://localhost:9000/page1.html`
- **Method**: `POST`
- **Expected Status**: `200 OK`
- **Expected Response (Text)**:
  ```text
  Crawling started
  ```

#### 2. Success Case: Check Crawl Status
- **URL**: `http://localhost:8081/crawler/status`
- **Method**: `GET`
- **Expected Status**: `200 OK`
- **Expected Response (JSON)**:
  ```text
  "COMPLETED"
  ```
  *(Returns `"RUNNING"` while active, `"COMPLETED"` once queue is empty).*

#### 3. Success Case: Inspect Visited URLs
- **URL**: `http://localhost:8081/crawler/visited`
- **Method**: `GET`
- **Expected Status**: `200 OK`
- **Expected Response (JSON Array)**:
  ```json
  [
    "http://localhost:9000/page1.html",
    "http://localhost:9000/page2.html",
    "http://localhost:9000/page3.html",
    "http://localhost:9000/page4.html"
  ]
  ```

#### 4. Reset Case: Clear Crawler State
- **URL**: `http://localhost:8081/crawler/state`
- **Method**: `DELETE`
- **Expected Status**: `200 OK`
- **Expected Response (Text)**:
  ```text
  Crawler state cleared
  ```

---

## F. Implementation explained

### Source Files & Responsibilities

| File Path | Class / Interface | Responsibility |
|---|---|---|
| `.../service/CrawlerService.java` | `CrawlerService` | Implements `@Async public void crawl(String seedUrl)`. Manages the main crawling BFS loop, coordinates queue, visited set, and JSoup HTML extraction. |
| `.../service/VisitedUrlService.java` | `VisitedUrlService` | Manages Redis Set with key `crawler:visited`. Provides `isVisited()` (`opsForSet().isMember`), `markVisited()`, and `clearVisitedUrls()`. |
| `.../service/CrawlStateService.java` | `CrawlStateService` | Tracks discovered URLs in Redis Set `crawler:discovered` and overall lifecycle status in Redis String `crawler:status`. |
| `.../service/UrlQueue.java` | `UrlQueue` | In-memory frontier queue backed by Java's thread-safe `LinkedBlockingQueue<String>`. |
| `.../service/UrlValidator.java` | `UrlValidator` | Validates that scheme is HTTP or HTTPS and host is non-null. |
| `.../controller/CrawlerController.java` | `CrawlerController` | Exposes REST endpoints to start, inspect, and reset the crawler. |

### Core Crawling Algorithm & Traversal Logic

1. **State Initialization**:
   - Check if already `RUNNING`; if so, ignore new start request.
   - Validate seed URL format via `UrlValidator`.
   - Set status to `RUNNING` in Redis.
   - Enqueue seed URL into `UrlQueue` and mark as discovered in Redis.

2. **Main Crawl Loop**:
   ```java
   while ((url = urlQueue.getNext()) != null) {
       if (!urlValidator.isValid(url) || visitedUrlService.isVisited(url)) {
           continue;
       }
       visitedUrlService.markVisited(url);

       try {
           Document document = Jsoup.connect(url).get();
           Elements links = document.select("a[href]");

           for (Element link : links) {
               String discoveredUrl = link.absUrl("href");
               if (urlValidator.isValid(discoveredUrl)) {
                   crawlStateService.markDiscovered(discoveredUrl);
                   if (!visitedUrlService.isVisited(discoveredUrl)) {
                       urlQueue.add(discoveredUrl);
                   }
               }
           }
       } catch (Exception e) {
           System.out.println("Failed to crawl: " + url);
       }
   }
   crawlStateService.setStatus(CrawlStatus.COMPLETED);
   ```

3. **Termination Condition**:
   The crawler halts and transitions to `COMPLETED` when `urlQueue.getNext()` returns `null` (the queue is completely empty and no further unvisited links remain).

---

## G. Verification and expected results

### 1. Execute Safe Closed-Loop Test
With Python server running on port 9000 serving `crawler_dataset/`:

- **PowerShell**:
  ```powershell
  # 1. Reset state
  Invoke-RestMethod -Uri "http://localhost:8081/crawler/state" -Method Delete

  # 2. Trigger crawl
  Invoke-RestMethod -Uri "http://localhost:8081/crawler/start?seedUrl=http://localhost:9000/page1.html" -Method Post

  # 3. Wait 2 seconds and inspect status
  Start-Sleep -Seconds 2
  Invoke-RestMethod -Uri "http://localhost:8081/crawler/status"

  # 4. View visited pages
  Invoke-RestMethod -Uri "http://localhost:8081/crawler/visited"
  ```

- **Curl (Bash)**:
  ```bash
  curl -X DELETE http://localhost:8081/crawler/state
  curl -X POST "http://localhost:8081/crawler/start?seedUrl=http://localhost:9000/page1.html"
  sleep 2
  curl http://localhost:8081/crawler/status
  curl http://localhost:8081/crawler/visited
  ```

### 2. Inspect Redis Data Structures via `redis-cli`
```bash
docker exec -it webcrawler-redis redis-cli SMEMBERS "crawler:visited"
```
**Expected Output**:
```text
1) "http://localhost:9000/page1.html"
2) "http://localhost:9000/page2.html"
3) "http://localhost:9000/page3.html"
4) "http://localhost:9000/page4.html"
```

---

## H. JMeter testing (where applicable)

### JMeter Test Plan for Crawler Status & Query API

> [!NOTE]
> Load-testing the `POST /crawler/start` endpoint with 100 concurrent users is counterproductive because `CrawlerService` checks `if (status == RUNNING) return;` (it only executes one concurrent crawl at a time).
> Instead, load-test the read-heavy state retrieval endpoints (`GET /crawler/visited` and `GET /crawler/status`):

1. **Create Thread Group**:
   - Number of Threads: `20`
   - Ramp-up: `1`
   - Loop Count: `20`
2. **Add HTTP Request Sampler**:
   - Method: `GET`
   - Server Name: `localhost`
   - Port: `8081`
   - Path: `/crawler/visited`
3. **Add Listeners**:
   - `Summary Report`
   - `Graph Results`
4. **Metrics to Inspect**:
   - Throughput querying Redis Set members.
   - Error Rate: `0.00%`.

---

## I. Common errors and quick fixes

1. **Error**: `Crawler runs indefinitely and never finishes`
   - **Cause**: Pointed the seed URL to a live external website (such as `https://google.com` or `https://en.wikipedia.org`). The source code has **no depth limit or domain boundary**, so it will crawl the entire internet.
   - **Fix**: Only use the controlled local dataset: `http://localhost:9000/page1.html`.
2. **Error**: `Nonexistent endpoint GET /crawler/health in requests.http returns 404`
   - **Cause**: `requests.http` contains a request for `/crawler/health`, but `CrawlerController.java` never implemented this endpoint.
   - **Fix**: Use `GET /crawler/status` instead to verify application state.
3. **Error**: `Docker build fails: COPY target/webcrawler-0.0.1-SNAPSHOT.jar app.jar not found`
   - **Cause**: Attempted `docker compose up --build` before compiling the jar locally.
   - **Fix**: Run `.\mvnw.cmd clean package -DskipTests` first to generate the JAR in `target/`.
4. **Error**: `Port 8081 already in use`
   - **Cause**: `UrlShortener` or `UrlShortenerRedis` is running on port 8081.
   - **Fix**: Stop the other service before launching `webcrawler`.

---

## J. Exam quick revision

### Clean-Start Command Sequence
```powershell
# 1. Start Redis
docker compose up -d redis

# 2. Start local HTTP server for sample pages (in separate terminal)
python -m http.server 9000 --directory webcrawler\crawler_dataset

# 3. Start Spring Boot crawler
cd webcrawler
.\mvnw.cmd spring-boot:run

# 4. Trigger crawl
Invoke-RestMethod -Uri "http://localhost:8081/crawler/start?seedUrl=http://localhost:9000/page1.html" -Method Post
```

### 3 Key Files / Methods to Remember
1. `CrawlerService.java`: `@Async crawl()` method containing while-loop over `urlQueue` and JSoup parsing.
2. `VisitedUrlService.java`: `isMember()` and `add()` on Redis Set `crawler:visited`.
3. `CrawlerController.java`: Endpoints `/crawler/start`, `/status`, `/visited`, `/state`.

### Likely Viva Questions & Answers
- **Q1: Why use a Redis Set for visited URLs rather than an in-memory HashSet?**
  *A*: A Redis Set enables distributed deduplication across multiple crawler worker nodes and survives application crashes, ensuring no page is crawled twice even in horizontal scale-out.
- **Q2: How does the crawler prevent infinite loops on circular hyperlinks (e.g., A -> B -> A)?**
  *A*: Before fetching any URL, `visitedUrlService.isVisited(url)` queries Redis. If already visited, the URL is skipped immediately.
- **Q3: What critical crawler safety features are missing from this implementation?**
  *A*: 1) **Politeness policy** (no delay between requests to avoid DoS on servers); 2) **Robots.txt compliance**; 3) **Depth limit / Max page count**; 4) **Same-domain restriction**.

### Cleanup Commands
```powershell
docker compose down -v
```

---

## Known Issues / Setup Blockers

1. **Dockerfile Requires Pre-built JAR (Build Blocker)**:
   - *Location*: `webcrawler/Dockerfile` (line 5: `COPY target/webcrawler-0.0.1-SNAPSHOT.jar app.jar`).
   - *Exam Impact*: Running `docker compose up --build` from a fresh clone fails because the JAR does not exist in `target/`. The student must build the JAR locally first using `mvn clean package -DskipTests` before Docker build can succeed.
2. **Missing Endpoint Documented in `requests.http`**:
   - *Location*: `requests.http` (lines 21–22: `GET http://localhost:8081/crawler/health`).
   - *Exam Impact*: There is no `@GetMapping("/health")` in `CrawlerController.java`. Testing this endpoint returns `HTTP 404 Not Found`. Use `GET /crawler/status` instead.
3. **Unbounded Crawl Risk (No Domain or Depth Filter)**:
   - *Location*: `CrawlerService.java` and `UrlValidator.java`.
   - *Exam Impact*: Feeding any external web URL causes an unbounded crawl that does not terminate. Always test against the local python server (`localhost:9000/page1.html`).
4. **Port 8081 Collision**:
   - *Location*: `src/main/resources/application.properties` sets `server.port=8081`.
   - *Exam Impact*: Cannot run concurrently with `UrlShortener` or `UrlShortenerRedis`.
