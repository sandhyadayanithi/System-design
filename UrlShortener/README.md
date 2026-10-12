# URL Shortener — MongoDB Persistence

## A. Purpose

This project implements a classic **URL Shortener service** (similar to Bitly or TinyURL) built with Spring Boot and MongoDB. The primary system design concept demonstrated is **Deterministic Hash-Based URL Compaction with Dynamic Collision Resolution** and **HTTP 302 Redirection**. The application generates compact 6-character short codes using SHA-256 hashing over original URLs, guarantees idempotency by reusing existing mappings, resolves hash collisions by appending random characters, and redirects client requests to the original destination using standard HTTP status codes.

```
 Client (POST /shorten) ──▶ SHA-256 Hash ──▶ Take first 6 hex chars ──▶ Check MongoDB for Collision
                                                                               │
                                                                   No ◄────────┴────────► Yes
                                                                   │                       │
                                                      Save to MongoDB                Append Random Char
                                                      Return Short URL                      │
                                                                                     Re-check Collision

 Client (GET /{shortCode}) ──▶ Query MongoDB ──▶ Return HTTP 302 with Location: <longUrl>
```

---

## B. Prerequisites and setup

### Required Tools & Versions
- **Java JDK**: 17 or higher (`java -version`)
- **Maven**: 3.6+ or included Maven Wrapper (`mvnw.cmd` / `./mvnw`)
- **Docker Engine**: CLI runtime for running MongoDB (`docker --version`)
- **MongoDB**: `mongo:latest` or `mongo:7.0` container image

### Step-by-Step Setup Commands

#### 1. Navigate to Project Directory
- **PowerShell (Windows)**:
  ```powershell
  cd c:\Users\sandh\Documents\sem5\System-design\UrlShortener
  ```
- **Bash (Linux/macOS)**:
  ```bash
  cd UrlShortener
  ```

#### 2. Start Required MongoDB Service
The application requires MongoDB running on host port `27017`.
- **PowerShell / Bash**:
  ```bash
  docker run -d --name url-mongo -p 27017:27017 mongo:latest
  ```

#### 3. Verify MongoDB is Running
- **PowerShell / Bash**:
  ```bash
  docker ps --filter "name=url-mongo"
  ```
  Expected: Container `url-mongo` running with `0.0.0.0:27017->27017/tcp`.

---

## C. Docker setup (terminal only)

### Docker Engine Verification
- **PowerShell**:
  ```powershell
  docker version
  docker ps
  ```

### Inspecting MongoDB
- **Check MongoDB logs**:
  ```bash
  docker logs url-mongo --tail 20
  ```
- **Open MongoDB Shell (`mongosh`)**:
  ```bash
  docker exec -it url-mongo mongosh --eval "show dbs"
  ```
- **Database & Collection**:
  - Database: `urlshortenerdb`
  - Collection: `urlMapping`
- **Inspect stored URLs**:
  ```bash
  docker exec -it url-mongo mongosh urlshortenerdb --eval "db.urlMapping.find().pretty()"
  ```
- **Stop and remove container**:
  ```bash
  docker stop url-mongo
  docker rm url-mongo
  ```

---

## D. Run the application

- **Working Directory**: `UrlShortener/`
- **Entry Point**: `com.example.urlshortener.UrlShortenerApplication`
- **Configuration File**: `src/main/resources/application.properties`
  - `spring.application.name=UrlShortener`
  - `spring.data.mongodb.uri=mongodb://localhost:27017/urlshortenerdb`
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
| `POST` | `http://localhost:8081/shorten` | `Content-Type: application/json` | `{"longUrl":"https://google.com"}` | `200 OK` | Shorten original long URL and store in MongoDB |
| `GET` | `http://localhost:8081/{shortCode}` | None | None | `302 Found` | Redirect client to destination `Location` |
| `GET` | `http://localhost:8081/nonexistent` | None | None | `404 Not Found` | Fail gracefully when short code is absent |

### Copy-Pasteable Postman Test Cases

#### 1. Shorten URL (Success Case)
- **URL**: `http://localhost:8081/shorten`
- **Method**: `POST`
- **Headers**: `Content-Type: application/json`
- **Body**:
  ```json
  {
    "longUrl": "https://www.wikipedia.org"
  }
  ```
- **Expected Status**: `200 OK`
- **Expected Response (JSON)**:
  ```json
  {
    "shortUrl": "http://localhost:8081/4f8b2a"
  }
  ```
  *(Note: The returned 6-character code matches the first 6 hex characters of SHA-256).*

#### 2. Test Redirection (Postman / Browser / Curl)
In Postman, disable *Automatically follow redirects* in request settings to observe the 302 header:
- **URL**: `http://localhost:8081/4f8b2a` *(use generated shortCode)*
- **Method**: `GET`
- **Expected Status**: `302 Found`
- **Expected Response Header**:
  ```text
  Location: https://www.wikipedia.org
  ```

#### 3. Error Case (Non-Existent Short Code)
- **URL**: `http://localhost:8081/xyz999`
- **Method**: `GET`
- **Expected Status**: `404 Not Found`

---

## F. Implementation explained

### Source Files & Responsibilities

| File Path | Class / Interface | Responsibility |
|---|---|---|
| `.../controller/urlController.java` | `urlController` | Exposes `POST /shorten` and `GET /{shortCode}` with `ResponseEntity.status(302).location(URI.create(longUrl))`. |
| `.../service/urlService.java` | `urlService` | Computes SHA-256 hash, generates 6-character hex substring, handles collision resolution by appending random characters, and verifies idempotency. |
| `.../repository/urlRepository.java` | `urlRepository` | Spring Data Mongo repository extending `MongoRepository<urlMapping, String>` with `findByLongUrl()` and `findByShortUrl()`. |
| `.../model/urlMapping.java` | `urlMapping` | Mongo document model with `@Id String id`, `String longUrl`, `String shortUrl`. |
| `.../dto/urlRequest.java` & `urlResponse.java` | DTOs | Request and response wrappers for payload serialization. |

### Core Shortening & Redirection Algorithm

1. **Idempotency Check**:
   ```java
   Optional<urlMapping> existing = repo.findByLongUrl(longUrl);
   if (existing.isPresent()) {
       return existing.get().getShortUrl();
   }
   ```
2. **SHA-256 Hash Generation**:
   ```java
   MessageDigest md = MessageDigest.getInstance("SHA-256");
   byte[] hash = md.digest(longUrl.getBytes(StandardCharsets.UTF_8));
   StringBuilder sb = new StringBuilder();
   for (byte b : hash) {
       sb.append(String.format("%02x", b));
   }
   String shortUrl = sb.substring(0, 6); // First 6 hex characters
   ```
3. **Collision Detection & Resolution**:
   ```java
   while (repo.findByShortUrl(shortUrl).isPresent()) {
       shortUrl += randomCharacter(); // Appends random char from [A-Za-z0-9]
   }
   ```
4. **Persistence & Return**:
   - Stores `urlMapping(null, longUrl, shortUrl)` into MongoDB.
   - Returns full URI: `"http://localhost:8081/" + shortUrl`.
5. **Lookup & Redirection (`getLongUrl`)**:
   - Queries `repo.findByShortUrl(shortUrl)`.
   - If found, `urlController` returns HTTP 302 with header `Location: <longUrl>`.

---

## G. Verification and expected results

### 1. Terminal Functional Verification
- **PowerShell**:
  ```powershell
  # 1. Shorten URL
  $res = Invoke-RestMethod -Uri "http://localhost:8081/shorten" -Method Post -ContentType "application/json" -Body '{"longUrl":"https://github.com"}'
  Write-Host "Short URL: $($res.shortUrl)"

  # 2. Extract code and test redirect without auto-following
  $code = $res.shortUrl.Split("/")[-1]
  $redirect = Invoke-WebRequest -Uri "http://localhost:8081/$code" -MaximumRedirection 0 -ErrorAction SilentlyContinue
  Write-Host "Redirect Status: $($redirect.StatusCode) -> Location: $($redirect.Headers.Location)"
  ```

- **Curl (Bash)**:
  ```bash
  # Test redirect with curl (shows headers without following)
  curl -I http://localhost:8081/<shortCode>
  ```
  **Expected Output**:
  ```text
  HTTP/1.1 302
  Location: https://github.com
  ```

### 2. Verify Database in MongoDB
Inspect MongoDB documents directly:
```bash
docker exec -it url-mongo mongosh urlshortenerdb --eval "db.urlMapping.find()"
```
**Expected Output**:
```json
{ "_id": ObjectId("..."), "longUrl": "https://github.com", "shortUrl": "..." }
```

---

## H. JMeter testing (where applicable)

### JMeter Test Plan for Redirection Throughput

1. **Create Thread Group**:
   - Number of Threads: `20`
   - Ramp-up: `2`
   - Loop Count: `10`
2. **Add HTTP Request Sampler**:
   - Method: `GET`
   - Server Name: `localhost`
   - Port: `8081`
   - Path: `/<shortCode>` *(use valid generated short code)*
   - **Important**: In Advanced / HTTP Request options, uncheck *Follow Redirects* to measure the latency of the URL shortener redirection service itself rather than the target external website.
3. **Add Listeners**:
   - `Summary Report`
   - `View Results Tree`
4. **Metrics to Inspect**:
   - Response Code: `302`
   - Throughput (req/sec) querying MongoDB without caching.

---

## I. Common errors and quick fixes

1. **Error**: `MongoSocketOpenException: Exception opening socket ... Connection refused: localhost:27017`
   - **Cause**: MongoDB container is not running on port 27017.
   - **Fix**: Run `docker run -d --name url-mongo -p 27017:27017 mongo:latest`.
2. **Error**: `Port 8081 already in use`
   - **Cause**: `UrlShortenerRedis` or `webcrawler` is running on port 8081.
   - **Fix**: Stop the conflicting application before starting `UrlShortener`.
3. **Error**: `Curl / Browser automatically opens the long URL instead of showing 302`
   - **Cause**: HTTP clients by default follow 302 redirects.
   - **Fix**: In curl, use `curl -I` (head only). In Postman, toggle off *Follow Redirects*.

---

## J. Exam quick revision

### Clean-Start Command Sequence
```powershell
# 1. Start MongoDB
docker run -d --name url-mongo -p 27017:27017 mongo:latest

# 2. Run Application
cd UrlShortener
.\mvnw.cmd spring-boot:run

# 3. Create Short URL
Invoke-RestMethod -Uri "http://localhost:8081/shorten" -Method Post -ContentType "application/json" -Body '{"longUrl":"https://google.com"}'
```

### 3 Key Files to Remember
1. `urlService.java`: `createShortUrl()` (SHA-256 hashing + collision loop) and `getLongUrl()`.
2. `urlController.java`: `ResponseEntity.status(302).location(URI.create(longUrl)).build()`.
3. `urlRepository.java`: `findByLongUrl()` and `findByShortUrl()`.

### Key Differences vs `UrlShortenerRedis`
- `UrlShortener`: Pure MongoDB. Every redirect hits the database directly. No caching layer.
- `UrlShortenerRedis`: Implements Redis Cache-Aside for `getLongUrl()`. Checks Redis first; falls back to MongoDB on cache miss.

### Likely Viva Questions & Answers
- **Q1: Why return HTTP 302 instead of HTTP 301?**
  *A*: HTTP 301 is Moved Permanently (cached aggressively by browsers, bypassing future analytics tracking). HTTP 302 is Found / Temporary Redirect (forces browsers to hit the shortener each time, enabling click tracking and traffic analytics).
- **Q2: How does the application prevent duplicate short URLs for the same original link?**
  *A*: Idempotent check in `urlService`: queries `repo.findByLongUrl(longUrl)` before generating a new hash; if found, returns the existing short URL.
- **Q3: How are hash collisions handled?**
  *A*: When `repo.findByShortUrl(shortUrl)` is present, a random alphanumeric character is repeatedly appended to the hash substring until a unique key is obtained.

### Cleanup Commands
```powershell
docker stop url-mongo; docker rm url-mongo
```

---

## Known Issues / Setup Blockers

1. **No Docker Compose File Provided**:
   - *Location*: Workspace folder `UrlShortener`.
   - *Exam Impact*: There is no `docker-compose.yml`. Students must start MongoDB manually via `docker run -d --name url-mongo -p 27017:27017 mongo:latest`.
2. **Port Conflict on 8081**:
   - *Location*: `src/main/resources/application.properties` specifies `server.port=8081`.
   - *Exam Impact*: Port 8081 is identical to `UrlShortenerRedis` and `webcrawler`. Cannot run concurrently with either project without changing `server.port`.
3. **Empty Initial README**:
   - *Location*: Original `UrlShortener/README.md` contained only `# System-design`.