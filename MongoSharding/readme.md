# MongoDB Horizontal Sharding — Docker Compose

Sharded MongoDB cluster (config server + 2 shards + mongos router) via Docker Compose.

## Prerequisites

- Docker Engine
- Docker Compose

## Project Structure

```
MongoSharding/
├── docker-compose.yml
└── README.md
```

## 1. Start the Cluster

```bash
docker compose up -d
docker ps
```

Expect 4 running containers: `configsvr`, `shard1`, `shard2`, `mongos`.

## 2. Initialize Replica Sets

**Config server**

```bash
docker exec -it configsvr mongosh --port 27019
```

```javascript
rs.initiate({
  _id: "configReplSet",
  configsvr: true,
  members: [{ _id: 0, host: "configsvr:27019" }]
})
```

**Shard 1**

```bash
docker exec -it shard1 mongosh --port 27018
```

```javascript
rs.initiate({
  _id: "shard1ReplSet",
  members: [{ _id: 0, host: "shard1:27018" }]
})
```

**Shard 2**

```bash
docker exec -it shard2 mongosh --port 27020
```

```javascript
rs.initiate({
  _id: "shard2ReplSet",
  members: [{ _id: 0, host: "shard2:27020" }]
})
```

## 3. Configure the Cluster via mongos

```bash
docker exec -it mongos mongosh
```

```javascript
sh.addShard("shard1ReplSet/shard1:27018")
sh.addShard("shard2ReplSet/shard2:27020")

sh.enableSharding("College")

use College

db.Student.createIndex({ RollNo: 1 })

sh.shardCollection("College.Student", { RollNo: 1 })
```

## 4. Load Test Data

```javascript
db.Student.insertMany([
  { RollNo: 1, Name: "Alice", Department: "CSE", Year: 2 },
  { RollNo: 2, Name: "Bob", Department: "ECE", Year: 3 },
  { RollNo: 3, Name: "Charlie", Department: "IT", Year: 1 },
  { RollNo: 4, Name: "David", Department: "EEE", Year: 4 },
  { RollNo: 5, Name: "Eva", Department: "CSE", Year: 2 }
])

for (let i = 6; i <= 1000; i++) {
  db.Student.insertOne({
    RollNo: i,
    Name: "Student" + i,
    Department: "CSE",
    Year: 1
  })
}
```

## 5. (Optional) Manual Chunk Management

```javascript
sh.splitAt("College.Student", { RollNo: 500 })
sh.moveChunk("College.Student", { RollNo: 600 }, "shard1ReplSet")
```

## Testing / Verification

Run these inside the `mongos` shell (`docker exec -it mongos mongosh`).

**Cluster health**

```javascript
sh.status()
db.Student.getShardDistribution()
```

**Data integrity**

```javascript
show dbs
show collections
db.Student.countDocuments()          // expect 1000+
```

**Query routing**

```javascript
db.Student.find({ RollNo: 25 })
db.Student.find({ Department: "CSE" })
```

**CRUD smoke test**

```javascript
// Create
db.Student.insertOne({ RollNo: 1001, Name: "John", Department: "AI", Year: 4 })

// Read
db.Student.find({ RollNo: 1001 })

// Update
db.Student.updateOne({ RollNo: 1001 }, { $set: { Department: "CSE" } })

// Delete
db.Student.deleteOne({ RollNo: 1001 })
```

## Teardown

```bash
docker compose down -v
```