# MongoDB Horizontal Sharding

## How It Works
- **Config Server:** Stores sharding metadata.
- **Shard 1 & Shard 2:** Store different portions of the data.
- **mongos:** Routes queries to the correct shard.
- **Shard Key (`RollNo`):** Determines how documents are partitioned.

## 1. Start the Cluster

Run from the folder containing `docker-compose.yml`.

**PowerShell**
```powershell
docker compose up -d
docker compose ps
```

**Ubuntu**
```bash
docker compose up -d
docker compose ps
```

> If a port is already in use, stop the other container using it or change the port mapping in `docker-compose.yml`.

## 2. Initialize Replica Sets

Run each command in PowerShell or Ubuntu.

**Config Server**
```bash
docker exec -it configsvr mongosh --port 27019
```
Inside `mongosh`:
```javascript
rs.initiate({
  _id: "configReplSet",
  configsvr: true,
  members: [{ _id: 0, host: "configsvr:27019" }]
})
exit
```

**Shard 1**
```bash
docker exec -it shard1 mongosh --port 27018
```
Inside `mongosh`:
```javascript
rs.initiate({
  _id: "shard1ReplSet",
  members: [{ _id: 0, host: "shard1:27018" }]
})
exit
```

**Shard 2**
```bash
docker exec -it shard2 mongosh --port 27020
```
Inside `mongosh`:
```javascript
rs.initiate({
  _id: "shard2ReplSet",
  members: [{ _id: 0, host: "shard2:27020" }]
})
exit
```

## 3. Configure Sharding

Connect to `mongos`:
```bash
docker exec -it mongos mongosh
```

Run inside `mongosh`:
```javascript
sh.addShard("shard1ReplSet/shard1:27018")
sh.addShard("shard2ReplSet/shard2:27020")
sh.enableSharding("College")
use College
db.Student.createIndex({ RollNo: 1 })
sh.shardCollection("College.Student", { RollNo: 1 })
```

## 4. Insert and Verify Data

Run inside `mongos`:
```javascript
use College
db.Student.insertMany([
  { RollNo: 1, Name: "Alice", Department: "CSE" },
  { RollNo: 600, Name: "Bob", Department: "ECE" }
])
db.Student.find()
sh.status()
db.Student.getShardDistribution()
```

## 5. Postman Testing

MongoDB does **not** provide a REST API by default, so standard Postman HTTP requests cannot directly test this cluster.

Use `mongosh` instead:

```javascript
db.Student.find({ RollNo: 1 }).explain("executionStats")
db.Student.find({ Department: "CSE" }).explain("executionStats")
```

- Query by `RollNo`: can target the relevant shard.
- Query by `Department`: generally searches across both shards.

## 6. Stop the Cluster

**PowerShell / Ubuntu**
```bash
docker compose stop
```

To remove containers and stored data:
```bash
docker compose down -v
```

**Note:** `down -v` deletes the cluster's persisted database volumes. Replica-set initialization and shard registration are normally one-time setup steps; don't repeat them on an already initialized cluster.
