# Web Crawler — Spring Boot, Redis & JSoup

An asynchronous, breadth-first-search (BFS) web crawler built using Spring Boot and JSoup. It supports job-based crawling, Redis-backed state management, domain restrictions, depth and page limits, failure tracking, and API-based monitoring.

## Features

- Asynchronous BFS crawling
- Redis-backed queue and job state, with in-memory fallback
- Visited, discovered, queued, and failed URL tracking
- Configurable maximum pages and crawl depth
- Domain restriction and HTTP/HTTPS URL validation
- Request timeout and politeness delay
- REST APIs for starting and monitoring crawl jobs
- Postman API testing
- JMeter load testing
- Local HTML dataset for predictable testing

## Tech Stack

- Java and Spring Boot
- Maven
- JSoup
- Redis
- Docker Compose
- Python HTTP server for the test dataset
- Postman
- Apache JMeter

## Project Structure

```text
webcrawler/
├── crawler_dataset/
│   ├── page1.html
│   ├── page2.html
│   ├── page3.html
│   ├── page4.html
│   ├── error-demo.html
│   └── section/
│       ├── page5.html
│       ├── page6.html
│       └── page7.html
├── postman/
│   └── WebCrawler.postman_collection.json
├── jmeter/
│   └── webcrawler_test_plan.jmx
├── docker-compose.yml
├── mvnw
└── README.md
```

Verify the actual filenames and directory structure in your checkout.

## Architecture

1. A client submits a crawl request through the REST API.
2. The crawler initializes a job and queues its seed URL.
3. URLs are processed asynchronously using BFS.
4. JSoup fetches HTML pages and extracts links.
5. Valid links are checked against domain, depth, and page limits.
6. Redis stores job metadata, queues, discovered URLs, visited URLs, and failures.
7. REST APIs expose job status and crawl results.

### Redis Key Pattern

```text
crawler:jobs
crawler:active_jobs
crawler:latest_job
crawler:job:{jobId}:meta
crawler:job:{jobId}:queue
crawler:job:{jobId}:visited
crawler:job:{jobId}:discovered
crawler:job:{jobId}:failed
```

## Prerequisites

Install or configure the following:

- Java (compatible with the project's configured version)
- Docker Engine and Docker Compose
- Python 3
- Maven Wrapper included in the project
- Postman (optional, for API testing)
- Apache JMeter (optional, for load testing)

Check your installations:

```bash
java -version
python3 --version
docker --version
docker compose version
```

From the project root:

```bash
cd ~/Documents/sem5/System-design/webcrawler
```

Replace this path with the actual location of your project on Ubuntu.

## 1. Start Redis

Open **Terminal 1**:

```bash
cd ~/Documents/sem5/System-design/webcrawler
docker compose up -d redis
docker compose ps
docker exec -it webcrawler-redis redis-cli ping
```

Expected Redis response:

```text
PONG
```

### If port 6379 is already in use

Check which container publishes port 6379:

```bash
docker ps --filter "publish=6379"
```

Check processes listening on the port:

```bash
sudo ss -ltnp | grep ':6379'
```

Stop a conflicting container only if it is safe to do so.

Alternatively, change the Redis host port mapping in `docker-compose.yml` to:

```yaml
ports:
  - "6380:6379"
```

If the Spring Boot application runs directly on Ubuntu, update its Redis connection configuration to use port `6380`.

Then recreate Redis:

```bash
docker compose up -d --force-recreate redis
docker compose ps
docker compose port redis 6379
```

Verify the actual container name before using commands that reference `webcrawler-redis`.

## 2. Start the Local HTML Dataset Server

Open **Terminal 2**:

```bash
cd ~/Documents/sem5/System-design/webcrawler
python3 -m http.server 9000 --directory crawler_dataset
```

Keep this terminal running.

Test the dataset in your browser:

```text
http://localhost:9000/page1.html
```

The crawler's seed URL will use this local server.

## 3. Start the Spring Boot Application

Open **Terminal 3**:

```bash
cd ~/Documents/sem5/System-design/webcrawler
chmod +x mvnw
./mvnw spring-boot:run
```

Keep this terminal running.

The application is expected to run on port `8081`, subject to the project's configuration.

## 4. Test the Health Endpoint Using Postman

Open Postman and create a new HTTP request.

- Method: `GET`
- URL: `http://localhost:8081/crawler/health`

Click **Send**.

Verify that the application responds successfully.

## 5. Configure Postman Collection Variables

Import the collection:

```text
postman/WebCrawler.postman_collection.json
```

Set these collection variables:

| Variable | Value |
|---|---|
| `baseUrl` | `http://localhost:8081` |
| `jobId` | `lab8-postman-test` |
| `seedUrl` | `http://localhost:9000/page1.html` |

## 6. Start a Crawl Job Using Postman

Create a new request with the following configuration:

- Method: `POST`
- URL: `{{baseUrl}}/crawler/start`
- Body: **raw → JSON**
- Header: `Content-Type: application/json`

Request body:

```json
{
  "seedUrl": "http://localhost:9000/page1.html",
  "jobId": "lab8-postman-test",
  "maxPages": 15,
  "maxDepth": 3,
  "allowedDomain": "localhost",
  "stayOnDomain": true,
  "requestTimeoutMs": 5000,
  "politenessDelayMs": 50
}
```

Click **Send** and inspect the response.

All integer fields must contain numeric values rather than `null`.

## 7. Check Crawl Status

After starting the crawl, create the following requests in Postman.

### A. Get Job Status

- Method: `GET`
- URL: `{{baseUrl}}/crawler/status?jobId={{jobId}}`

### B. Get Job Details

- Method: `GET`
- URL: `{{baseUrl}}/crawler/jobs/{{jobId}}`

Inspect the response for the job ID, seed URL, status, page limits, and crawl progress.

## 8. Inspect Crawling Results Using Postman

### A. Visited URLs

- Method: `GET`
- URL: `{{baseUrl}}/crawler/visited?jobId={{jobId}}&page=0&size=20`

### B. Discovered URLs

- Method: `GET`
- URL: `{{baseUrl}}/crawler/discovered?jobId={{jobId}}&page=0&size=20`

### C. Queue

- Method: `GET`
- URL: `{{baseUrl}}/crawler/queue?jobId={{jobId}}&page=0&size=20`

### D. Failed URLs

- Method: `GET`
- URL: `{{baseUrl}}/crawler/failed?jobId={{jobId}}`

### E. List All Jobs

- Method: `GET`
- URL: `{{baseUrl}}/crawler/jobs`

### F. Get Job Status Through the Job-Specific Endpoint

- Method: `GET`
- URL: `{{baseUrl}}/crawler/jobs/{{jobId}}/status`

For each request, click **Send** and inspect the response body and HTTP status code.

## 9. Expected Dataset Behavior

The test dataset contains linked pages for validating BFS traversal and depth restrictions.

- `page1.html` links to pages such as `page2.html`, `page3.html`, and `error-demo.html`.
- `page2.html` and `page3.html` link to `page4.html` and `section/page5.html`.
- `page4.html` and `page5.html` link to `section/page6.html`.
- `section/page6.html` links to `section/page7.html`.
- The missing `does-not-exist-404.html` page can be used to test failure tracking.
- External and non-HTTP links should be rejected according to the crawler's URL validation rules.

With `maxDepth = 3`, `page7.html` should not be fetched if it is at depth 4 under the dataset's link structure.

Actual results depend on the implementation's depth convention, URL normalization, and asynchronous processing.

## 10. Delete Job State Using Postman

Run this request only after completing the other tests.

- Method: `DELETE`
- URL: `{{baseUrl}}/crawler/jobs/{{jobId}}/state`

Click **Send** and inspect the response.

## 11. Run Automated Tests

Open a new Ubuntu terminal in the project root:

```bash
./mvnw test
```

Build the project:

```bash
./mvnw clean package
```

Review the actual Maven output to confirm whether the tests pass.

## 12. Run with Docker Compose

If your Docker Compose configuration includes the Spring Boot application service:

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f
```

If the Spring Boot app runs inside Docker, `localhost:9000` refers to the app container itself, not the Ubuntu host. Configure the seed URL to use an address reachable from the container, such as `http://host.docker.internal:9000/page1.html` where supported, and configure host-gateway networking if necessary.

If Compose only defines Redis, run the Python dataset server and Spring Boot application separately as described above.

## 13. JMeter Load Testing

Install Apache JMeter using your preferred Ubuntu installation method and ensure the `jmeter` command is available on your `PATH`.

From the project directory:

```bash
cd ~/Documents/sem5/System-design/webcrawler/jmeter

jmeter -n \
  -t webcrawler_test_plan.jmx \
  -Jhost=localhost \
  -Jport=8081 \
  -Jthreads=25 \
  -Jrampup=5 \
  -Jloops=10 \
  -l results.jtl \
  -e \
  -o dashboard-report
```

The test results are written to `results.jtl`, and the HTML report is generated in `dashboard-report`.

Ensure the output directory does not already exist before generating the report. Do not treat the configured thread count as a measured performance result; use the generated JMeter report to evaluate actual results.

## Troubleshooting

### Redis port conflict

In Ubuntu, inspect port usage with:

```bash
sudo ss -ltnp | grep ':6379'
docker ps --filter "publish=6379"
```

Resolve the port conflict or change the host port mapping and Spring Boot Redis configuration.

### Docker is not running

```bash
docker info
docker compose ps
```

Start Docker Engine using your Ubuntu installation's service configuration if necessary.

### Python server is unavailable

```bash
python3 --version
```

Confirm that the dataset server is running in Terminal 2 and that `crawler_dataset/` exists. You can also open `http://localhost:9000/page1.html` in a browser.

### Spring Boot application is unavailable

Open the Spring Boot terminal and inspect the startup logs. Verify that the application is configured to use port `8081`.

### Unsupported Content-Type

In Postman, select **Body → raw → JSON** and ensure the request header is:

```text
Content-Type: application/json
```

### Integer field parsing errors

Provide numeric values for all required integer fields, including:

```json
{
  "maxPages": 15,
  "maxDepth": 3,
  "requestTimeoutMs": 5000,
  "politenessDelayMs": 50
}
```

Do not send `null` for required primitive integer fields.

### Job metadata is IDLE or contains null fields

If a start request returns default metadata such as `status: IDLE`, `seedUrl: null`, `maxPages: 0`, or `maxDepth: 0`, verify the request DTO mapping and job initialization.

Trace the request through:

1. JSON deserialization
2. `CrawlerController`
3. `CrawlRequest`
4. Job initialization
5. Redis state storage
6. Response serialization
7. Asynchronous crawl execution

Try a new job ID to avoid confusing stale state with a new request. If the problem persists, fix the mapping or initialization logic and add a regression test verifying that the submitted seed URL, job ID, limits, and domain are persisted and that asynchronous crawling starts.

## 14. Stop the Services

Stop the Python HTTP server and Spring Boot application with `Ctrl+C` in their respective terminals.

Stop Redis:

```bash
docker compose stop redis
```

To stop the full Compose stack, if applicable:

```bash
docker compose down
```

## Verification Checklist

- [ ] Redis starts and responds with `PONG`.
- [ ] The local dataset is accessible on port `9000`.
- [ ] Spring Boot starts on port `8081`.
- [ ] The health endpoint responds successfully in Postman.
- [ ] A crawl request creates and initializes the expected job.
- [ ] Job metadata contains the submitted configuration.
- [ ] BFS traversal discovers and visits expected URLs.
- [ ] Depth and page limits are respected.
- [ ] Failed URLs are tracked.
- [ ] Postman requests return the expected results.
- [ ] Maven tests and packaging complete successfully.
- [ ] JMeter results are reviewed if load testing is required.

**Note:** Verify endpoint paths, request DTO fields, Redis configuration, container names, and project structure against the current checkout. These instructions assume the previously described implementation and do not claim that the project has been independently tested on Ubuntu.


## 9. Expected Dataset Behavior

The test dataset contains linked pages for validating BFS traversal and depth restrictions.

- `page1.html` links to pages such as `page2.html`, `page3.html`, and `error-demo.html`.
- `page2.html` and `page3.html` link to `page4.html` and `section/page5.html`.
- `page4.html` and `page5.html` link to `section/page6.html`.
- `section/page6.html` links to `section/page7.html`.
- The missing `does-not-exist-404.html` page can be used to test failure tracking.
- External and non-HTTP links should be rejected according to the crawler's URL validation rules.

With `maxDepth = 3`, `page7.html` should not be fetched if it is at depth 4 under the dataset's link structure.

Actual results depend on the implementation's depth convention, URL normalization, and asynchronous processing.

## 10. Run Automated Tests

Run the test suite:

```bash
./mvnw test
```

Build the project:

```bash
./mvnw clean package
```

Review the actual Maven output to confirm whether the tests pass.

## 11. Run with Docker Compose

If your Docker Compose configuration includes the Spring Boot application service:

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f
```

If the Spring Boot app runs inside Docker, `localhost:9000` refers to the app container itself, not the Ubuntu host. Configure the seed URL to use an address reachable from the container, such as `http://host.docker.internal:9000/page1.html` where supported, and configure host-gateway networking if necessary.

If Compose only defines Redis, run the Python dataset server and Spring Boot application separately as described above.

## 12. JMeter Load Testing

Install Apache JMeter using your preferred Ubuntu installation method and ensure the `jmeter` command is available on your `PATH`.

From the project directory:

```bash
cd ~/Documents/sem5/System-design/webcrawler/jmeter
jmeter -n \
  -t webcrawler_test_plan.jmx \
  -Jhost=localhost \
  -Jport=8081 \
  -Jthreads=25 \
  -Jrampup=5 \
  -Jloops=10 \
  -l results.jtl \
  -e \
  -o dashboard-report
```

The test results are written to `results.jtl`, and the HTML report is generated in `dashboard-report`.

Ensure the output directory does not already exist before generating the report. Do not treat the configured thread count as a measured performance result; use the generated JMeter report to evaluate actual results.

## Troubleshooting

### Redis port conflict

```bash
sudo ss -ltnp | grep ':6379'
docker ps --filter "publish=6379"
```

Resolve the port conflict or change the host port mapping and Spring Boot Redis configuration.

### Docker is not running

```bash
docker info
docker compose ps
```

Start Docker Engine using your Ubuntu installation's service configuration if necessary.

### Python server is unavailable

```bash
python3 --version
curl -I http://localhost:9000/page1.html
```

Confirm that the dataset server is running in Terminal 2 and that `crawler_dataset/` exists.

### Spring Boot application is unavailable

```bash
curl -i http://localhost:8081/crawler/health
```

Inspect the Spring Boot terminal for startup failures or port conflicts.

### Unsupported Content-Type

Ensure the request sends:

```text
Content-Type: application/json
```

When using `curl`, include:

```bash
-H "Content-Type: application/json"
```

### Integer field parsing errors

Provide numeric values for all required integer fields, including:

```json
{
  "maxPages": 15,
  "maxDepth": 3,
  "requestTimeoutMs": 5000,
  "politenessDelayMs": 50
}
```

Do not send `null` for required primitive integer fields.

### Job metadata is IDLE or contains null fields

If a start request returns default metadata such as `status: IDLE`, `seedUrl: null`, `maxPages: 0`, or `maxDepth: 0`, verify the request DTO mapping and job initialization.

Trace the request through:

1. JSON deserialization
2. `CrawlerController`
3. `CrawlRequest`
4. Job initialization
5. Redis state storage
6. Response serialization
7. Asynchronous crawl execution

Try a new job ID to avoid confusing stale state with a new request. If the problem persists, fix the mapping or initialization logic and add a regression test verifying that the submitted seed URL, job ID, limits, and domain are persisted and that asynchronous crawling starts.

## 13. Stop the Services

Stop the Python HTTP server and Spring Boot application with `Ctrl+C` in their respective terminals.

Stop Redis:

```bash
docker compose stop redis
```

To stop the full Compose stack, if applicable:

```bash
docker compose down
```

## Verification Checklist

- [ ] Redis starts and responds with `PONG`.
- [ ] The local dataset is accessible on port `9000`.
- [ ] Spring Boot starts on port `8081`.
- [ ] The health endpoint responds successfully.
- [ ] A crawl request creates and initializes the expected job.
- [ ] Job metadata contains the submitted configuration.
- [ ] BFS traversal discovers and visits expected URLs.
- [ ] Depth and page limits are respected.
- [ ] Failed URLs are tracked.
- [ ] Postman requests return the expected results.
- [ ] Maven tests and packaging complete successfully.
- [ ] JMeter results are reviewed if load testing is required.

**Note:** Verify endpoint paths, request DTO fields, Redis configuration, container names, and project structure against the current checkout. The commands above assume the previously described implementation and are not a claim that the project has been independently tested on Ubuntu.
