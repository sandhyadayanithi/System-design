# Autocomplete Search System — Trie + Redis

## 1. How it works

- **First request:** Searches the Trie for matching prefixes → stores results in Redis (`TRIE`).
- **Repeated request:** Returns cached results from Redis (`CACHE`).
- Cache entries expire after **300 seconds**.

## 2. Start the services

Ensure Docker Desktop (Windows) or Docker Engine (Ubuntu) is running.

**PowerShell**
```powershell
cd C:\Users\sandh\Documents\sem5\System-design\autocomplete-system
docker compose up --build -d
docker compose ps
```

**Ubuntu**
```bash
cd ~/autocomplete-system
docker compose up --build -d
docker compose ps
```

Docker Compose starts both Redis and the Spring Boot application.

## 3. Test the API

**Postman**

- Method: `GET`
- URL: `http://localhost:8080/api/autocomplete?prefix=app&limit=5`
- First request: `"source": "TRIE"`
- Second request: `"source": "CACHE"`
- Expected status: `200 OK`

**Check health**

```text
http://localhost:8080/api/health
```

## 4. Useful commands

**View application logs**

```bash
docker compose logs -f app
```

**Inspect Redis cache**

```bash
docker exec -it autocomplete-redis redis-cli KEYS "autocomplete:*"
```

**Clear cache**

```bash
docker exec -it autocomplete-redis redis-cli FLUSHALL
```

**Stop all services**

```bash
docker compose down
```

## 5. Important

- No `mvnw` or `mvnw.cmd` is included; use Docker Compose to run the project.
- If port `8080` is already occupied, stop the conflicting application first.
