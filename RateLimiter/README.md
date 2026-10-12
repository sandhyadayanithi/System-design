# Rate Limiter — Redis + Spring Boot

## 1. How It Works

- Each client sends an `X-Client-Id` header.
- Redis stores a separate token bucket for each client.
- Bucket capacity: **10 tokens**; refill rate: **2 tokens/second**.
- Each request consumes one token.
- Tokens available → `200 OK`.
- No tokens available → `429 Too Many Requests`.
- A Redis Lua script executes the token update atomically to prevent race conditions.

## 2. Prerequisites

- Java 17+
- Docker
- Maven Wrapper included in the project

## 3. Start Redis

Navigate to the project directory.

**PowerShell**
```powershell
cd "C:\Users\sandh\Documents\sem5\System-design\RateLimiter"
docker compose up -d
docker ps
docker exec -it rate-limiter-redis redis-cli ping
```

**Ubuntu**
```bash
cd ~/RateLimiter
docker compose up -d
docker ps
docker exec -it rate-limiter-redis redis-cli ping
```

Expected response: `PONG`.

*Replace the Ubuntu path with your actual project directory.*

## 4. Start the Spring Boot Application

Keep Redis running. Open another terminal in the project directory.

**PowerShell**
```powershell
.\mvnw.cmd spring-boot:run
```

**Ubuntu**
```bash
chmod +x ./mvnw
./mvnw spring-boot:run
```

API base URL: `http://localhost:8080`

Keep this terminal open while testing.

## 5. Test Using Postman

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/test`
- **Header:** `X-Client-Id: client-100`
- **Expected:** `200 OK` while tokens are available.

### Test Rate Limiting

Send 15 rapid requests using the same client ID.

**PowerShell**
```powershell
1..15 | ForEach-Object {
  try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/test" -Headers @{"X-Client-Id"="burst-test"}
    "$_ : HTTP $($r.StatusCode)"
  } catch {
    "$_ : HTTP $([int]$_.Exception.Response.StatusCode)"
  }
}
```

**Ubuntu**
```bash
for i in {1..15}; do
  curl -s -o /dev/null -w "$i : HTTP %{http_code}\n" \
    -H "X-Client-Id: burst-test" \
    http://localhost:8080/api/test
done
```

Expected: Requests initially return `200`; requests exceeding the available tokens return `429`. Token refill may occur between requests.

### Test Missing Client ID

Send a request without the `X-Client-Id` header.

Expected: `400 Bad Request`.

## 6. Inspect Redis

List rate-limiter keys:

```bash
docker exec -it rate-limiter-redis redis-cli KEYS "rate_limit:*"
```

Inspect a client's token bucket:

```bash
docker exec -it rate-limiter-redis redis-cli HGETALL "rate_limit:burst-test"
```

## 7. Stop Services

Stop the Spring Boot application using `Ctrl+C`.

Stop Redis:

```bash
docker compose stop
```

Remove containers and volumes (**deletes Redis data**):

```bash
docker compose down -v
```

## 8. Common Errors

- **Redis connection error:** Run `docker compose up -d` and verify the container with `docker ps`.
- **Port 8080 already in use:** Stop the other application using port 8080.
- **Requests always return 200:** This implementation uses fail-open behavior when Redis is unavailable. Verify that Redis is running.
- **Rate limit doesn't trigger:** Use a fresh client ID and send requests rapidly.

## 9. Viva — Quick Revision

1. **Token Bucket:** Allows controlled bursts while limiting sustained traffic.
2. **Redis:** Stores shared token state across application instances.
3. **Lua script:** Makes token refill and consumption atomic.
4. **HTTP 429:** Indicates that the client has exceeded its rate limit.
5. **Fail-open:** Allows requests if Redis becomes unavailable, prioritizing service availability over rate-limit enforcement.
