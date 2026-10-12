# URL Shortener — Redis Cache + MongoDB

## 1. How it works

- **POST `/shorten`**: Generates a short URL and stores it in MongoDB.
- **First GET request**: Cache miss → fetches the URL from MongoDB → stores it in Redis.
- **Next GET requests**: Cache hit → retrieves the URL directly from Redis.
- Returns **302 redirect** for valid short URLs and **404** for unknown codes.

## 2. Start the services

Ensure Docker Desktop (Windows) or Docker Engine (Ubuntu) is running.

**PowerShell (Windows)**

```powershell
docker start url-redis
docker start url-mongo
```

**Ubuntu**

```bash
docker start url-redis
docker start url-mongo
```

If the containers don't exist yet, create them once:

```bash
docker run -d --name url-redis -p 6379:6379 redis:7-alpine
docker run -d --name url-mongo -p 27017:27017 mongo:latest
```

Verify:

```bash
docker ps
```

## 3. Start the application

Navigate to the project folder first.

**PowerShell**

```powershell
cd C:\Users\sandh\Documents\sem5\System-design\UrlShortenerRedis
.\mvnw.cmd spring-boot:run
```

**Ubuntu**

```bash
cd ~/UrlShortenerRedis
chmod +x ./mvnw
./mvnw spring-boot:run
```

Application URL: `http://localhost:8081`

Keep the terminal open while the application runs.

## 4. Test the APIs

**Create a short URL**

- Method: `POST`
- URL: `http://localhost:8081/shorten`
- Body → raw → JSON

```json
{
  "longUrl": "https://spring.io"
}
```

**Test redirection**

Use the short code returned by the POST request.

- Method: `GET`
- URL: `http://localhost:8081/SHORT_CODE`
- First request: `CACHE MISS`
- Second request: `CACHE HIT`
- Expected status: `302 Found`

Disable **Follow Redirects** in Postman to see the 302 response.

## 5. Useful commands

**Check Redis**

```bash
docker exec -it url-redis redis-cli ping
docker exec -it url-redis redis-cli KEYS "*"
```

**Check MongoDB records**

```bash
docker exec -it url-mongo mongosh urlshortenerdb --eval "db.urlMapping.find()"
```

**Stop the application:** Press `Ctrl + C`.

**Stop the services**

```bash
docker stop url-redis url-mongo
```

**Remove containers** (optional)

```bash
docker rm url-redis url-mongo
```

To restart later, use `docker start url-redis` and `docker start url-mongo`.
