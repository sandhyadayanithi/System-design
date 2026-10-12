# URL Shortener — Spring Boot + MongoDB

## 1. How It Works

- Accepts a long URL through `POST /shorten`.
- Generates a 6-character code using SHA-256 hashing.
- Checks MongoDB for duplicate URLs and short-code collisions.
- Stores the mapping in MongoDB.
- Redirects requests to the original URL using HTTP `302 Found`.

## 2. Start MongoDB

### PowerShell
```powershell
docker start url-mongo
```

If the container doesn't exist, create it:

```powershell
docker run -d --name url-mongo -p 27017:27017 mongo:latest
```

### Ubuntu
```bash
docker start url-mongo
```

If the container doesn't exist, create it:

```bash
docker run -d --name url-mongo -p 27017:27017 mongo:latest
```

Verify MongoDB:

```bash
docker ps
```

## 3. Start the Application

Open a second terminal in the project directory.

### PowerShell
```powershell
cd "C:\Users\sandh\Documents\sem5\System-design\UrlShortener"
.\mvnw.cmd spring-boot:run
```

### Ubuntu
```bash
cd ~/UrlShortener
chmod +x ./mvnw
./mvnw spring-boot:run
```

API base URL: `http://localhost:8081`

Keep both MongoDB and the Spring Boot application running.

## 4. Test the API

### Create a Short URL

**Postman**
- Method: `POST`
- URL: `http://localhost:8081/shorten`
- Header: `Content-Type: application/json`
- Body → raw → JSON:

```json
{
  "longUrl": "https://www.google.com"
}
```

Expected: `200 OK` with a generated `shortUrl`.

### PowerShell
```powershell
Invoke-RestMethod -Uri "http://localhost:8081/shorten" -Method Post -ContentType "application/json" -Body '{"longUrl":"https://www.google.com"}'
```

### Ubuntu
```bash
curl -X POST http://localhost:8081/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://www.google.com"}'
```

### Test Redirection

Replace `SHORT_CODE` with the code returned by the previous request.

**PowerShell**
```powershell
Invoke-WebRequest -Uri "http://localhost:8081/SHORT_CODE" -MaximumRedirection 0
```

**Ubuntu**
```bash
curl -i http://localhost:8081/SHORT_CODE
```

Expected: `302 Found` with a `Location` header containing the original URL.

## 5. Check MongoDB Data

### PowerShell / Ubuntu
```bash
docker exec -it url-mongo mongosh urlshortenerdb --eval "db.urlMapping.find().pretty()"
```

## 6. Stop Services

Stop Spring Boot using `Ctrl+C`.

Stop MongoDB:

```bash
docker stop url-mongo
```

Start it again later with:

```bash
docker start url-mongo
```

## 7. Viva Quick Revision

- **SHA-256:** Generates a deterministic hash from the original URL.
- **MongoDB:** Persists the original URL and its short code.
- **Collision handling:** Appends random alphanumeric characters if a short code already exists.
- **HTTP 302:** Redirects the client to the original URL.
- **Idempotency:** Repeated requests for the same URL reuse the existing mapping.
