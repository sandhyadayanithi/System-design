# Consistent Hashing with Virtual Nodes

## How It Works
- Student ID is hashed using MD5.
- A `TreeMap` hash ring with 3 virtual nodes per MongoDB node determines where data is stored.
- Adding or removing a node triggers data migration.
- Spring Boot runs on port `8080`; MongoDB uses ports `27017–27020`.

## 1. Start MongoDB

Start Docker Desktop first on Windows.

**PowerShell**
```powershell
docker start ch-mongo1 ch-mongo2 ch-mongo3
```

If the containers don't exist yet, create them once:

```powershell
docker run -d --name ch-mongo1 -p 27017:27017 mongo:latest
docker run -d --name ch-mongo2 -p 27018:27017 mongo:latest
docker run -d --name ch-mongo3 -p 27019:27017 mongo:latest
docker run -d --name ch-mongo4 -p 27020:27017 mongo:latest
```

**Ubuntu**
```bash
docker start ch-mongo1 ch-mongo2 ch-mongo3
```

Create the containers once if needed, using the same `docker run` commands above.

Check status:
```bash
docker ps
```

## 2. Start Spring Boot

Run from the inner `consistentHashing` directory containing `pom.xml`.

**PowerShell**
```powershell
cd ConsistentHashing\consistentHashing
.\mvnw.cmd spring-boot:run
```

**Ubuntu**
```bash
cd ConsistentHashing/consistentHashing
chmod +x mvnw
./mvnw spring-boot:run
```

## 3. Test the API

**PowerShell — Add a student**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/students" -Method Post -ContentType "application/json" -Body '{"id":"101","name":"Alice","department":"CSE"}'
```

**Ubuntu — Add a student**
```bash
curl -X POST http://localhost:8080/api/students \
-H "Content-Type: application/json" \
-d '{"id":"101","name":"Alice","department":"CSE"}'
```

**Both — Retrieve a student**
```bash
curl http://localhost:8080/api/students/101
```

## 4. Add or Remove a Node

**Add `mongo4`**
```bash
curl -X POST http://localhost:8080/api/students/node/mongo4
```

**Remove `mongo4`**
```bash
curl -X DELETE http://localhost:8080/api/students/node/mongo4
```

For PowerShell, use `Invoke-RestMethod -Uri "URL" -Method Post` or `-Method Delete` instead of `curl` if needed.

## 5. Stop Services

Stop Spring Boot with `Ctrl + C`.

**PowerShell / Ubuntu**
```bash
docker stop ch-mongo1 ch-mongo2 ch-mongo3 ch-mongo4
```

**Remember:** `docker start` works only after the containers have been created. Use `docker run` once for initial setup.

## 6. Postman Test Cases

**Base URL:** `http://localhost:8080`

### Test 1: Add a Student
- **Method:** `POST`
- **URL:** `http://localhost:8080/api/students`
- **Headers:** `Content-Type: application/json`
- **Body → raw → JSON:**

```json
{
  "id": "101",
  "name": "Alice",
  "department": "CSE"
}
```

**Expected:** Success message showing which MongoDB node stored the student.

### Test 2: Retrieve a Student
- **Method:** `GET`
- **URL:** `http://localhost:8080/api/students/101`

**Expected:** Student details for Alice.

### Test 3: Add Another Student
- **Method:** `POST`
- **URL:** `http://localhost:8080/api/students`
- **Headers:** `Content-Type: application/json`
- **Body → raw → JSON:**

```json
{
  "id": "102",
  "name": "Bob",
  "department": "ECE"
}
```

**Expected:** Success message showing the node selected by consistent hashing.

### Test 4: Add a MongoDB Node
- **Method:** `POST`
- **URL:** `http://localhost:8080/api/students/node/mongo4`

**Expected:** Confirmation that `mongo4` was added and data migration was triggered.

### Test 5: Remove a MongoDB Node
- **Method:** `DELETE`
- **URL:** `http://localhost:8080/api/students/node/mongo4`

**Expected:** Confirmation that `mongo4` was removed and data migration was triggered.

### Test 6: Retrieve a Non-existent Student
- **Method:** `GET`
- **URL:** `http://localhost:8080/api/students/99999`

**Expected:** Empty response (`null`) if the student does not exist.

### Verify the Results
1. Add students using the POST requests.
2. Retrieve them using GET.
3. Add or remove `mongo4`.
4. Retrieve the students again to verify that their records remain accessible after migration.

**Important:** Run the add-node test before the remove-node test. Ensure all required MongoDB containers are running and the Spring Boot application is started.
