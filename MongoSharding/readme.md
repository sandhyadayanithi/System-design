# MongoDB Horizontal Sharding with Docker Compose

## A. Purpose

This project implements a fully distributed **Horizontal Sharding Cluster** in MongoDB 7.0 using Docker Compose. The core system design concept demonstrated is **Database Horizontal Partitioning (Sharding)**, which distributes large collections across independent database servers to scale storage capacity and throughput beyond the limits of a single machine. The cluster consists of two shard replica sets (`shard1`, `shard2`), a dedicated configuration server replica set (`configsvr`), and a query router (`mongos`) that acts as a single point of contact for clients to transparently route queries using a range-based shard key (`RollNo`).

```
                              [Client / mongosh]
                                      │
                                      ▼
                             [mongos Query Router]
                                 (Port 27017)
                                  │         │
                 ┌────────────────┘         └────────────────┐
                 ▼                                           ▼
      [Config Server Replica Set]               ┌────────────┴────────────┐
      configsvr (Port 27019)                   ▼                         ▼
      Metadata & Chunk Mapping           [Shard 1 Replica Set]     [Shard 2 Replica Set]
                                         shard1 (Port 27018)       shard2 (Port 27020)
                                         Range: RollNo < 500       Range: RollNo >= 500
```

---

## B. Prerequisites and setup

### Required Tools & Versions
- **Docker Engine & Docker Compose**: CLI only (`docker --version`, `docker compose version`)
- **MongoDB Shell (`mongosh`)**: Installed locally or run inside Docker containers via `docker exec`
- **MongoDB Compass** *(optional GUI client)*: Connects via URI if port 27017 is exposed

### Step-by-Step Setup Commands

#### 1. Navigate to Project Directory
- **PowerShell (Windows)**:
  ```powershell
  cd c:\Users\sandh\Documents\sem5\System-design\MongoSharding
  ```
- **Bash (Linux/macOS)**:
  ```bash
  cd MongoSharding
  ```

#### 2. Start the Docker Compose Cluster
- **PowerShell / Bash**:
  ```bash
  docker compose up -d
  ```

#### 3. Verify All 4 Containers are Running
- **PowerShell / Bash**:
  ```bash
  docker compose ps
  ```
  Expected running services: `configsvr`, `shard1`, `shard2`, `mongos`.

---

## C. Docker setup (terminal only)

### Docker Engine Verification
- **PowerShell**:
  ```powershell
  docker version
  docker ps
  ```

### Services & Network Architecture in `docker-compose.yml`

| Service Name | Container Name | Image | Internal Port | Host Port | Replica Set Name | Purpose |
|---|---|---|---|---|---|---|
| `configsvr` | `configsvr` | `mongo:7.0` | `27019` | *None* | `configReplSet` | Stores cluster metadata and chunk routing tables |
| `shard1` | `shard1` | `mongo:7.0` | `27018` | `27018:27018` | `shard1ReplSet` | Data storage node 1 |
| `shard2` | `shard2` | `mongo:7.0` | `27020` | `27020:27020` | `shard2ReplSet` | Data storage node 2 |
| `mongos` | `mongos` | `mongo:7.0` | `27017` | *None* | N/A | Query router interface for applications |

### Important Operational Commands
- **Check container logs**:
  ```bash
  docker logs mongos --tail 30
  docker logs configsvr --tail 30
  ```
- **Enter container shell**:
  ```bash
  docker exec -it mongos bash
  ```
- **Stop cluster without losing data**:
  ```bash
  docker compose stop
  ```
- **Tear down cluster and erase volumes (Clean reset)**:
  ```bash
  docker compose down -v
  ```
  *(The `-v` flag removes volumes `configdb`, `shard1db`, and `shard2db` to allow clean re-initialization).*

---

## D. Run and configure the cluster

Since MongoDB sharding requires initializing replica sets before registering shards, execute the following commands in exact order:

### Step 1: Initialize Config Server Replica Set
Connect into `configsvr`:
```bash
docker exec -it configsvr mongosh --port 27019
```
Execute inside `mongosh`:
```javascript
rs.initiate({
  _id: "configReplSet",
  configsvr: true,
  members: [{ _id: 0, host: "configsvr:27019" }]
});
exit;
```

### Step 2: Initialize Shard 1 Replica Set
Connect into `shard1`:
```bash
docker exec -it shard1 mongosh --port 27018
```
Execute inside `mongosh`:
```javascript
rs.initiate({
  _id: "shard1ReplSet",
  members: [{ _id: 0, host: "shard1:27018" }]
});
exit;
```

### Step 3: Initialize Shard 2 Replica Set
Connect into `shard2`:
```bash
docker exec -it shard2 mongosh --port 27020
```
Execute inside `mongosh`:
```javascript
rs.initiate({
  _id: "shard2ReplSet",
  members: [{ _id: 0, host: "shard2:27020" }]
});
exit;
```

### Step 4: Register Shards & Configure Sharding via `mongos`
Open the router shell:
```bash
docker exec -it mongos mongosh
```
Execute inside `mongos`:
```javascript
// 1. Add shards to the router
sh.addShard("shard1ReplSet/shard1:27018");
sh.addShard("shard2ReplSet/shard2:27020");

// 2. Enable sharding on database "College"
sh.enableSharding("College");

// 3. Switch to database "College"
use College;

// 4. Create an index on the chosen shard key
db.Student.createIndex({ RollNo: 1 });

// 5. Shard the collection using the index
sh.shardCollection("College.Student", { RollNo: 1 });
```

---

## E. API testing with Postman (where applicable)

> [!NOTE]
> MongoDB is a database engine operating on the binary MongoDB Wire Protocol, not an HTTP REST service. Standard HTTP Postman requests cannot talk directly to `mongos`.
> Functional testing and data manipulation must be performed through the MongoDB Shell (`mongosh`) or MongoDB Compass.

### MongoDB Compass Connection (If Host Port is Published)
- **Connection URI**: `mongodb://localhost:27017`
- *Note*: As documented in [Known Issues](#known-issues--setup-blockers), Compass requires exposing `27017:27017` on `mongos` in `docker-compose.yml`.

---

## F. Implementation explained

### Architecture Component Responsibilities

| Component | Responsibility in Sharded Setup |
|---|---|
| `configsvr` | Stores cluster catalog and metadata mapping collections and chunk ranges to shards. Uses replica set `configReplSet`. |
| `shard1` & `shard2` | Physical data partitions. Each runs as an independent `mongod --shardsvr` replica set. |
| `mongos` | Stateless routing process. Reads routing table from `configsvr`, intercepts client queries, and directs operations only to the shards holding the relevant data. |
| `RollNo` Shard Key | Range-based partitioning key determining which chunk a document belongs to. |

### Data Insertion & Distribution Flow

1. **Insert Sample Records**:
   Run in `mongos` (`docker exec -it mongos mongosh`):
   ```javascript
   use College;

   // Insert individual documents
   db.Student.insertMany([
     { RollNo: 1, Name: "Alice", Department: "CSE", Year: 2 },
     { RollNo: 2, Name: "Bob", Department: "ECE", Year: 3 },
     { RollNo: 3, Name: "Charlie", Department: "IT", Year: 1 },
     { RollNo: 4, Name: "David", Department: "EEE", Year: 4 },
     { RollNo: 5, Name: "Eva", Department: "CSE", Year: 2 }
   ]);

   // Insert 1,000 documents to generate distinct key ranges
   for (let i = 6; i <= 1000; i++) {
     db.Student.insertOne({
       RollNo: i,
       Name: "Student" + i,
       Department: "CSE",
       Year: 1
     });
   }
   ```

2. **Chunk Management: Chunk Splitting vs. Chunk Moving**:
   - **Chunk Splitting (`sh.splitAt`)**:
     Divides an existing chunk into two smaller chunks at a specific shard key boundary without moving data across servers.
     ```javascript
     sh.splitAt("College.Student", { RollNo: 500 });
     ```
   - **Chunk Migration (`sh.moveChunk`)**:
     Relocates a specific chunk from its current shard to another shard in the cluster.
     ```javascript
     sh.moveChunk("College.Student", { RollNo: 600 }, "shard2ReplSet");
     ```
   - *Key Distinction*: `splitAt` creates boundaries within the same shard; `moveChunk` physically migrates documents to another shard to rebalance cluster capacity.

---

## G. Verification and expected results

Execute the following commands inside `docker exec -it mongos mongosh`:

### 1. Verify Cluster Health & Shard Status
```javascript
sh.status();
```
**Expected Output**:
- Shows `shards` with `shard1ReplSet` and `shard2ReplSet`.
- Shows `autosplit: enabled`.
- Under `database: College`, shows `College.Student` partitioned with shard key `{ RollNo: 1 }`.

### 2. Verify Shard Distribution
```javascript
use College;
db.Student.getShardDistribution();
```
**Expected Output**:
```text
Shard shard1ReplSet at shard1:27018
 data : ~...KiB docs : 500 chunks : 1
 estimated data per chunk : ...

Shard shard2ReplSet at shard2:27020
 data : ~...KiB docs : 500 chunks : 1
 estimated data per chunk : ...
```
Documents are distributed across both shards.

### 3. Query Routing Verification (Targeted vs. Scatter-Gather)
- **Targeted Query (uses Shard Key)**:
  ```javascript
  db.Student.find({ RollNo: 25 }).explain("executionStats");
  ```
  *Expected*: `mongos` routes the query to **only one** single shard (`SINGLE_SHARD`).
- **Scatter-Gather Query (non-shard key attribute)**:
  ```javascript
  db.Student.find({ Department: "CSE" }).explain("executionStats");
  ```
  *Expected*: `mongos` broadcasts the query across **all shards** (`SHARD_MERGE`).

### 4. Direct Shard Verification
Inspect documents directly on individual shard ports from the host:
```bash
# Query Shard 1 directly
docker exec -it shard1 mongosh --port 27018 --eval "use College; db.Student.countDocuments()"

# Query Shard 2 directly
docker exec -it shard2 mongosh --port 27020 --eval "use College; db.Student.countDocuments()"
```
*Expected*: The sum of document counts across both shards equals the total cluster document count (1,000).

---

## H. JMeter testing (where applicable)

> [!NOTE]
> **HTTP JMeter Testing is Not Applicable**: MongoDB does not expose an HTTP/REST endpoint out of the box; it uses the custom binary MongoDB wire protocol over TCP. Standard JMeter HTTP Request samplers cannot interact with `mongos`.
>
> **Recommended Alternative Functional / Load Test**:
> To simulate concurrent high-volume traffic during the lab exam, run a concurrent insertion loop directly via `mongosh` or a shell script:
> ```bash
> # Run 5 background mongosh processes inserting batches concurrently
> for i in {1..5}; do
>   docker exec -i mongos mongosh College --eval "
>     for (let j = 1; j <= 200; j++) {
>       db.Student.insertOne({ RollNo: ($i * 1000) + j, Name: 'Bench' + j });
>     }
>   " &
> done
> wait
> ```

---

## I. Common errors and quick fixes

1. **Error**: `MongoServerError: no shards found`
   - **Cause**: Attempted to enable sharding or run operations before adding shards to `mongos`.
   - **Fix**: Run `sh.addShard("shard1ReplSet/shard1:27018")` and `sh.addShard("shard2ReplSet/shard2:27020")` in `mongos`.
2. **Error**: `Cannot connect to mongodb://localhost:27017 from Compass or host shell`
   - **Cause**: In `docker-compose.yml`, `mongos` does not specify `ports: - "27017:27017"`.
   - **Fix**: Use `docker exec -it mongos mongosh` inside the container, or add port mapping `27017:27017` to `mongos` service in `docker-compose.yml`.
3. **Error**: `sh.shardCollection failed: please create an index first`
   - **Cause**: Sharding a collection requires an index starting with the shard key.
   - **Fix**: Execute `db.Student.createIndex({ RollNo: 1 })` before calling `sh.shardCollection()`.
4. **Error**: `ReplSetNotFound / NotYetInitialized`
   - **Cause**: Commands executed on `mongos` before running `rs.initiate()` on `configsvr`, `shard1`, or `shard2`.
   - **Fix**: Connect to each container (`configsvr`, `shard1`, `shard2`) and run `rs.initiate(...)` first.

---

## J. Exam quick revision

### Clean-Start Command Sequence (Order of Execution)
```bash
# 1. Start Docker containers
docker compose up -d

# 2. Init Config Server
docker exec -it configsvr mongosh --port 27019 --eval 'rs.initiate({_id:"configReplSet",configsvr:true,members:[{_id:0,host:"configsvr:27019"}]})'

# 3. Init Shard 1
docker exec -it shard1 mongosh --port 27018 --eval 'rs.initiate({_id:"shard1ReplSet",members:[{_id:0,host:"shard1:27018"}]})'

# 4. Init Shard 2
docker exec -it shard2 mongosh --port 27020 --eval 'rs.initiate({_id:"shard2ReplSet",members:[{_id:0,host:"shard2:27020"}]})'

# 5. Connect to mongos and configure sharding
docker exec -it mongos mongosh
```
Inside `mongos`:
```javascript
sh.addShard("shard1ReplSet/shard1:27018");
sh.addShard("shard2ReplSet/shard2:27020");
sh.enableSharding("College");
use College;
db.Student.createIndex({ RollNo: 1 });
sh.shardCollection("College.Student", { RollNo: 1 });
```

### 3 Key Concepts to Remember
1. **Config Server**: Must be initialized with `configsvr: true`.
2. **`mongos` Router**: Stateless entry point; never holds application data directly.
3. **Targeted vs Scatter-Gather**: Queries containing the shard key hit 1 shard; queries without it hit all shards.

### Likely Viva Questions & Answers
- **Q1: What is the difference between vertical scaling and horizontal sharding?**
  *A*: Vertical scaling adds more CPU/RAM to a single machine. Horizontal sharding partitions data across multiple servers, scaling storage and I/O horizontally.
- **Q2: What is the purpose of the mongos process?**
  *A*: `mongos` is a query router and coordinator. It caches metadata from the config server, directs incoming operations to appropriate shards, and aggregates results.
- **Q3: What makes a good shard key?**
  *A*: High cardinality, even distribution of values (preventing hotspots), and alignment with frequent query filters to avoid scatter-gather queries.

### Cleanup Commands
```bash
docker compose down -v
```

---

## Known Issues / Setup Blockers

1. **Missing Host Port Mapping for `mongos`**:
   - *Location*: `MongoSharding/docker-compose.yml` (`mongos` service).
   - *Exam Impact*: `mongos` listens on port 27017 internally, but `ports:` is omitted. Connecting from host tools (such as MongoDB Compass or host-level `mongosh`) to `mongodb://localhost:27017` will fail with `Connection refused`. All commands must be run via `docker exec -it mongos mongosh`, unless `ports: ["27017:27017"]` is manually added to `docker-compose.yml`.
2. **Empty `init.sh` Script**:
   - *Location*: `MongoSharding/init.sh` (0-byte empty file).
   - *Exam Impact*: Running `bash init.sh` does nothing. Setup must be performed manually using the command sequence documented in Section D.
3. **MongoDB 6.0+ / 7.0 Command Syntax**:
   - *Location*: In MongoDB 7.0, `sh.enableSharding("db")` is optional (sharding a collection automatically enables it), but harmless. However, manual `sh.splitAt()` and `sh.moveChunk()` require balancer awareness and chunk boundaries; automatic balancing handles distribution once chunk sizes reach threshold.