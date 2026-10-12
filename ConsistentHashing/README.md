# Consistent Hashing with Virtual Nodes & MongoDB Sharding Simulation

## A. Purpose

This project implements a **Consistent Hashing Ring** with virtual nodes in Spring Boot to dynamically partition and route student records across multiple MongoDB database instances. The primary system design concept demonstrated is **Consistent Hashing with Virtual Nodes**, which minimizes key redistribution when nodes are added or removed compared to simple modular hashing ($hash(key) \pmod N$). The application maintains a sorted circular ring using Java's `TreeMap`, maps keys clockwise to the nearest server node, saves data to the designated node's database via distinct `MongoTemplate` beans, and automatically migrates affected records when the cluster topology changes.

```
       [Student Key (ID)] ──▶ MD5 Hash ──▶ Ring Lookup (tailMap)
                                                   │
               ┌────────── mongo1#0 ───────────────┤
               │                                   ▼
        [mongo3#2]                        [mongo2#0] (Target Node)
               │                                   │
               └────────── mongo2#1 ───────────────┘
                                                   │
                                     Save to studentdb2 on port 27018
```

---

## B. Prerequisites and setup

### Required Tools & Versions
- **Java JDK**: 17 or higher (`java -version`)
- **Maven**: 3.6+ or included Maven Wrapper (`mvnw.cmd` / `./mvnw`)
- **Docker Engine**: CLI runtime for running MongoDB instances (`docker --version`)
- **MongoDB**: `mongo:latest` or `mongo:7.0` container image

### Step-by-Step Setup Commands

#### 1. Navigate to Project Directory
> [!IMPORTANT]
> The Maven project root containing `pom.xml` and source code is located inside the `consistentHashing` subdirectory.

- **PowerShell (Windows)**:
  ```powershell
  cd c:\Users\sandh\Documents\sem5\System-design\ConsistentHashing\consistentHashing
  ```
- **Bash (Linux/macOS)**:
  ```bash
  cd ConsistentHashing/consistentHashing
  ```

#### 2. Start Required MongoDB Instances
The application expects up to 4 independent MongoDB instances running on host ports `27017`, `27018`, `27019`, and `27020`.

- **PowerShell (Windows)**:
  ```powershell
  docker run -d --name ch-mongo1 -p 27017:27017 mongo:latest
  docker run -d --name ch-mongo2 -p 27018:27018 mongo:latest
  docker run -d --name ch-mongo3 -p 27019:27019 mongo:latest
  docker run -d --name ch-mongo4 -p 27020:27020 mongo:latest
  ```
- **Bash (Linux/macOS)**:
  ```bash
  docker run -d --name ch-mongo1 -p 27017:27017 mongo:latest
  docker run -d --name ch-mongo2 -p 27018:27017 mongo:latest
  docker run -d --name ch-mongo3 -p 27019:27017 mongo:latest
  docker run -d --name ch-mongo4 -p 27020:27027 mongo:latest
  ```

#### 3. Verify MongoDB Services are Running
- **PowerShell / Bash**:
  ```bash
  docker ps --filter "name=ch-mongo"
  ```

---

## C. Docker setup (terminal only)

### Verify Docker Daemon
- **PowerShell**:
  ```powershell
  docker version
  docker ps
  ```
  *If Docker daemon is not running*: Start Docker Desktop service from PowerShell:
  ```powershell
  Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
  ```

### Inspecting MongoDB Containers
- **Check container logs**:
  ```bash
  docker logs ch-mongo1 --tail 20
  ```
- **Enter MongoDB shell (`mongosh`) inside a container**:
  ```bash
  docker exec -it ch-mongo1 mongosh --port 27017
  ```
- **Port Mapping**:
  | Container | Host Port | Database Name | MongoTemplate Bean |
  |---|---|---|---|
  | `ch-mongo1` | `27017` | `studentdb1` | `mongo1` (Primary) |
  | `ch-mongo2` | `27018` | `studentdb2` | `mongo2` |
  | `ch-mongo3` | `27019` | `studentdb3` | `mongo3` |
  | `ch-mongo4` | `27020` | `studentdb4` | `mongo4` (for scale-out test) |

- **Teardown & Volume Cleanup**:
  ```bash
  docker stop ch-mongo1 ch-mongo2 ch-mongo3 ch-mongo4
  docker rm ch-mongo1 ch-mongo2 ch-mongo3 ch-mongo4
  ```

---

## D. Run the application

- **Working Directory**: `ConsistentHashing/consistentHashing`
- **Entry Point**: `com.example.consistentHashing.ConsistentHashingApplication`
- **Configuration File**: `src/main/resources/application.properties`
  - `server.port=8080`
  - `spring.application.name=consistentHashing`
- **Default Port & Base URL**: `http://localhost:8080`

### Start Commands
- **PowerShell (Windows)**:
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```
- **Bash (Linux)**:
  ```bash
  ./mvnw spring-boot:run
  ```
- **Alternative (via system Maven)**:
  ```bash
  mvn spring-boot:run
  ```

### Expected Startup Output
On startup, `ConsistentHashingApplication` implements `CommandLineRunner` and prints the initial hash ring tokens:
```text
------------- HASH RING -------------
<hash_token_1> -> mongo1
<hash_token_2> -> mongo3
<hash_token_3> -> mongo2
...
Tomcat started on port 8080 (http) with context path '/'
Started ConsistentHashingApplication in X.XXX seconds
```

### Stop the Application
Press `Ctrl + C` in the running terminal.

---

## E. API testing with Postman (where applicable)

### Implemented Endpoints

| HTTP Method | Full Local URL | Required Headers | Example Request Body | Expected Status | Purpose |
|---|---|---|---|---|---|
| `POST` | `http://localhost:8080/api/students` | `Content-Type: application/json` | `{"id":"101","name":"Alice","department":"CSE"}` | `200 OK` | Hash student ID, route to node, and save |
| `GET` | `http://localhost:8080/api/students/101` | None | None | `200 OK` | Route to responsible node and fetch record |
| `POST` | `http://localhost:8080/api/students/node/mongo4` | None | None | `200 OK` | Add node `mongo4` to ring and migrate data |
| `DELETE` | `http://localhost:8080/api/students/node/mongo4` | None | None | `200 OK` | Remove node `mongo4` from ring and migrate data |

### Copy-Pasteable Postman Test Cases

#### 1. Success Case: Add Student (Routes to Node)
- **URL**: `http://localhost:8080/api/students`
- **Method**: `POST`
- **Headers**: `Content-Type: application/json`
- **Body**:
  ```json
  {
    "id": "101",
    "name": "Alice",
    "department": "CSE"
  }
  ```
- **Expected Response (Body)**:
  ```text
  Student Alice saved successfully in node: mongo2
  ```
  *(Note: The exact node depends on the MD5 hash of "101" mapped onto the ring).*

#### 2. Success Case: Retrieve Student
- **URL**: `http://localhost:8080/api/students/101`
- **Method**: `GET`
- **Expected Response (Body)**:
  ```json
  {
    "id": "101",
    "name": "Alice",
    "department": "CSE"
  }
  ```

#### 3. Scale-Out & Migration Test: Add Node
- **URL**: `http://localhost:8080/api/students/node/mongo4`
- **Method**: `POST`
- **Expected Response (Body)**:
  ```text
  Node mongo4 added to the hash ring and data migrated.
  ```

#### 4. Failure Case: Get Non-Existent Student
- **URL**: `http://localhost:8080/api/students/99999`
- **Method**: `GET`
- **Expected Status**: `200 OK` with empty body (null), as Spring Data MongoDB `findById` returns null when not present.

---

## F. Implementation explained

### Source Files & Responsibilities

| File Path | Class / Interface | Responsibility |
|---|---|---|
| `.../service/ConsistentHashService.java` | `ConsistentHashService` | Maintains `SortedMap<Long, String> ring`, computes 32-bit MD5 hashes, manages virtual nodes (`VIRTUAL_NODES = 3`), handles node addition/removal and clockwise key lookup. |
| `.../config/MongoConfig.java` | `MongoConfig` | Instantiates four separate `MongoTemplate` Spring beans named `mongo1`, `mongo2`, `mongo3`, `mongo4` pointing to localhost ports `27017`–`27020`. |
| `.../service/StudentService.java` | `StudentService` | Injects `ApplicationContext` to dynamically fetch the target `MongoTemplate` by node name; implements save, get, and topology-triggered `migrateData()`. |
| `.../controller/StudentController.java` | `StudentController` | Exposes REST endpoints for CRUD operations and dynamic ring modification. |
| `.../model/Student.java` | `Student` | Data model containing `id`, `name`, `department`. |

### Core Algorithm & Request Flow

1. **Ring Initialization**:
   - Constructor invokes `addNode("mongo1")`, `addNode("mongo2")`, `addNode("mongo3")`.
   - For each node, 3 virtual replicas are created: `node + "#0"`, `node + "#1"`, `node + "#2"`.
   - Each virtual replica is hashed into a 32-bit unsigned integer and placed in `TreeMap<Long, String>`.

2. **Hash Computation (`hash(String key)`)**:
   ```java
   MessageDigest md = MessageDigest.getInstance("MD5");
   byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
   long hash = 0;
   for (int i = 0; i < 4; i++) {
       hash <<= 8;
       hash |= (digest[i] & 0xFF);
   }
   return hash & 0xffffffffL;
   ```

3. **Clockwise Node Resolution (`getNode(String key)`)**:
   - Compute `hash = hash(key)`.
   - Retrieve `tailMap = ring.tailMap(hash)`.
   - If `tailMap.isEmpty()`, wrap around to the first node in the circle: `ring.get(ring.firstKey())`.
   - Otherwise, select the nearest successor: `tailMap.get(tailMap.firstKey())`.

4. **Dynamic Data Migration (`migrateData()`)**:
   - Triggered upon `addNodeAndMigrate()` or `removeNodeAndMigrate()`.
   - Iterates through all nodes `{"mongo1", "mongo2", "mongo3", "mongo4"}`.
   - For each student record, checks `targetNode = hashService.getNode(student.getId())`.
   - If `!sourceNode.equals(targetNode)`, copies student to `targetTemplate.save()` and removes from `sourceTemplate.remove()`.

---

## G. Verification and expected results

### 1. Verify Ring Distribution via Console
Upon application startup, verify the hash ring in terminal stdout:
```text
------------- HASH RING -------------
31241512 -> mongo1
85412948 -> mongo3
194820194 -> mongo2
...
```

### 2. Verify Database Storage in MongoDB Shell
Insert two students:
- Student 101 (`id: "101"`)
- Student 102 (`id: "102"`)

Check which node stored Student 101:
- Open shell in `ch-mongo1`:
  ```bash
  docker exec -it ch-mongo1 mongosh --port 27017 --eval "use studentdb1; db.student.find()"
  ```
- Open shell in `ch-mongo2`:
  ```bash
  docker exec -it ch-mongo2 mongosh --port 27018 --eval "use studentdb2; db.student.find()"
  ```
- Open shell in `ch-mongo3`:
  ```bash
  docker exec -it ch-mongo3 mongosh --port 27019 --eval "use studentdb3; db.student.find()"
  ```

**Expected Result**:
The document appears in **only one** specific database instance corresponding to its hash position on the ring.

---

## H. JMeter testing (where applicable)

### Minimal JMeter Test Plan for `POST /api/students`

1. **Create Thread Group**:
   - Number of Threads (Users): `10`
   - Ramp-up Period (seconds): `2`
   - Loop Count: `5`
2. **Add HTTP Header Manager**:
   - `Content-Type`: `application/json`
3. **Add HTTP Request Sampler**:
   - Protocol: `http`
   - Server Name or IP: `localhost`
   - Port Number: `8080`
   - Method: `POST`
   - Path: `/api/students`
   - Body Data (using JMeter variable `${__Random(100,999)}`):
     ```json
     {
       "id": "${__Random(100,999)}",
       "name": "Student_${__Random(100,999)}",
       "department": "CSE"
     }
     ```
4. **Add Listener**:
   - `View Results Tree`
   - `Summary Report`
5. **Key Metrics to Inspect**:
   - **Throughput**: Requests per second handled.
   - **Error %**: Must be `0.00%`.
   - **Response Time**: Verify low latency routing.

---

## I. Common errors and quick fixes

1. **Error**: `MongoSocketOpenException: Exception opening socket ... Connection refused: localhost:27017`
   - **Cause**: The MongoDB Docker containers are not running.
   - **Fix**: Run `docker run -d --name ch-mongo1 -p 27017:27017 mongo:latest` (and mongo2, mongo3 on ports 27018, 27019).
2. **Error**: `NoSuchBeanDefinitionException: No bean named 'mongoX' available`
   - **Cause**: Trying to add an invalid node name not configured in `MongoConfig.java`.
   - **Fix**: The code only defines beans `mongo1`, `mongo2`, `mongo3`, `mongo4`. Use only these node names.
3. **Error**: `Port 8080 already in use`
   - **Cause**: Another service (such as `RateLimiter` or `autocomplete-system`) is running on port 8080.
   - **Fix**: Stop the conflicting application or set `server.port=8082` in `application.properties`.
4. **Error**: `Cannot find pom.xml`
   - **Cause**: Executed `mvnw` from `System-design\ConsistentHashing` instead of the inner `ConsistentHashing\consistentHashing` folder.
   - **Fix**: `cd consistentHashing` before executing maven commands.

---

## J. Exam quick revision

### Clean-Start Command Sequence
```powershell
# 1. Start MongoDB nodes
docker run -d --name ch-mongo1 -p 27017:27017 mongo:latest
docker run -d --name ch-mongo2 -p 27018:27017 mongo:latest
docker run -d --name ch-mongo3 -p 27019:27017 mongo:latest

# 2. Enter project directory & run
cd ConsistentHashing\consistentHashing
.\mvnw.cmd spring-boot:run

# 3. Test insert (PowerShell)
Invoke-RestMethod -Uri "http://localhost:8080/api/students" -Method Post -ContentType "application/json" -Body '{"id":"101","name":"Alice","department":"CSE"}'
```

### 3 Key Files / Methods to Remember
1. `ConsistentHashService.java`: `hash()`, `addNode()`, `getNode()` (uses `ring.tailMap(hash)`).
2. `MongoConfig.java`: Definition of `@Bean(name = "mongo1")` through `mongo4`.
3. `StudentService.java`: `(MongoTemplate) context.getBean(targetNode)` and `migrateData()`.

### Core Algorithm in 3 Steps
1. Hash node names with replica indices (`node#i`) to place virtual nodes on a `TreeMap` ring.
2. Hash incoming keys with MD5 and find the next higher entry using `tailMap(hash).firstKey()` (wrapping to `firstKey()` if empty).
3. On node add/remove, scan existing records and migrate keys whose target node changed.

### Likely Viva Questions & Answers
- **Q1: Why use Virtual Nodes in Consistent Hashing?**
  *A*: Physical nodes can cause uneven key distribution (hotspots). Virtual nodes place multiple tokens per physical node across the ring to ensure balanced partition distribution.
- **Q2: What is the time complexity of locating a node?**
  *A*: $O(\log(N \times V))$, where $N$ is the number of physical nodes and $V$ is virtual nodes per node, using the Red-Black tree (`TreeMap`) `tailMap()` binary search.
- **Q3: What happens when a node is removed?**
  *A*: Only keys mapped to the removed node's token slices are re-routed to adjacent successor nodes; all other keys remain unaffected.

### Cleanup Commands
```powershell
docker rm -f ch-mongo1 ch-mongo2 ch-mongo3 ch-mongo4
```

---

## Known Issues / Setup Blockers

1. **Nested Directory Structure**:
   - *Location*: Root `ConsistentHashing` folder contains an inner `consistentHashing` directory housing the actual Maven project (`pom.xml`).
   - *Exam Impact*: Running `mvnw` from `ConsistentHashing` directly will fail with `The system cannot find the file specified`. Commands must run from `ConsistentHashing\consistentHashing`.
2. **Missing Docker Compose Configuration**:
   - *Location*: Workspace folder `ConsistentHashing`.
   - *Exam Impact*: Unlike other projects, there is no `docker-compose.yml` to automatically start the 4 MongoDB instances. Students must manually start individual containers via `docker run` as documented in Section B.
3. **Hardcoded Node Names in Migration**:
   - *Location*: `StudentService.java` (`String[] allNodes = {"mongo1", "mongo2", "mongo3", "mongo4"};`).
   - *Exam Impact*: Adding any node named other than `mongo1`..`mongo4` will throw a runtime exception because dynamic Spring beans are not defined in `MongoConfig.java`.
