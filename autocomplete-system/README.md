# Autocomplete Search System (Trie + Redis + Spring Boot)

A prefix-matching autocomplete service built with Spring Boot. Search terms are held
in an in-memory **Trie** for fast prefix lookups, and **Redis** is used as a
cache-aside layer so repeated prefix requests skip the Trie traversal entirely.

> UCS3513 System Design Laboratory — Lab Exercise 7: *Design and Simulate an
> Autocomplete Search System using Prefix Matching*.

```
Client ──GET /api/autocomplete?prefix=app&limit=10──▶ Spring Boot
                                                          │
                                          1. check Redis (cache-aside)
                                                          │
                                     HIT ◀────────────────┼────────────────▶ MISS
                                      │                                        │
                              return cached list                  2. DFS the Trie subtree
                                                                       for "app", sort by
                                                                       frequency, take top-K
                                                                              │
                                                                  3. store result in Redis
                                                                     (TTL = 300s) and return
```

---

## 1. Project layout

```
autocomplete-system/
├── pom.xml
├── Dockerfile
├── docker-compose.yml                  # app + its own redis:7-alpine
├── docker-compose.external-redis.yml   # app only, reuses YOUR existing redis container
├── src/main/java/com/example/autocomplete/
│   ├── AutocompleteApplication.java
│   ├── config/          RedisConfig.java, TrieConfig.java
│   ├── model/            SearchTerm.java
│   ├── trie/              TrieNode.java, Trie.java
│   ├── service/         AutocompleteService.java   (cache-aside logic)
│   ├── controller/     AutocompleteController.java, dto/AutocompleteResponse.java
│   └── loader/            DataLoader.java           (loads the CSV dataset at startup)
├── src/main/resources/
│   ├── application.yml
│   └── data/search_terms.csv           # dataset: term,frequency
├── src/test/java/...TrieTest.java      # unit tests for the Trie
├── postman/Autocomplete.postman_collection.json
├── jmeter/autocomplete_test_plan.jmx   # JMeter test plan (CLI/non-GUI friendly)
├── jmeter/prefixes.csv                 # prefixes JMeter cycles through
└── data/search_terms.csv               # copy of the dataset for reference
```

---

## 2. What it does

- **Dataset**: `src/main/resources/data/search_terms.csv` — 99 search terms with
  search-frequency counts (`term,frequency`), loaded into the Trie on startup by `DataLoader`.
- **Trie**: `Trie.insert(word, frequency)` builds the prefix tree; `Trie.searchByPrefix(prefix, limit)`
  walks to the prefix node, DFS-collects every complete word beneath it, sorts by frequency
  descending, and returns the top-K.
- **Redis caching**: `AutocompleteService` checks Redis first (key = `autocomplete:<prefix>:<limit>`).
  On a miss it queries the Trie, then writes the result back to Redis with a 5-minute TTL
  (configurable via `CACHE_TTL_SECONDS`). If Redis is unreachable, it falls back to the Trie
  automatically rather than failing the request.
- **REST API**:
  - `GET /api/autocomplete?prefix=app&limit=10` → suggestions + which layer served them (`CACHE` or `TRIE`) + latency in ms
  - `GET /api/health` → trie size, cache hit/miss counters
  - `GET /actuator/health` → Spring Boot Actuator health check

Example response (actual output from this build):
```json
{
  "prefix": "app",
  "count": 5,
  "source": "TRIE",
  "tookMillis": 3,
  "suggestions": [
    { "term": "apple", "frequency": 9500 },
    { "term": "app store", "frequency": 8900 },
    { "term": "application form", "frequency": 7200 },
    { "term": "appointment", "frequency": 6100 },
    { "term": "application software", "frequency": 5400 }
  ]
}
```

`limit` defaults to `10` and is capped at `50`. A blank `prefix`, a missing `prefix`,
or `limit <= 0` returns `400`. An unknown prefix returns `200` with `"count": 0`.

---

## 3. Prerequisites

- Docker Engine + Docker Compose plugin (`docker compose version`) — CLI only, no Docker
  Desktop GUI needed for anything below.
- A `redis:7-alpine` container already running (optional — see Option B).
- `curl` (for quick testing) and, optionally, `newman` (Postman's CLI runner) or the
  JMeter CLI (`jmeter -n ...`, no GUI needed).
- For the non-Docker path: JDK 17 + Maven 3.6+.

---

## 4. Build & run

### Option A — self-contained (app + its own Redis)
Simplest path: this spins up a **new** `redis:7-alpine` container dedicated to the app.

```bash
cd autocomplete-system
docker compose up --build -d
docker compose logs -f app        # watch for "Dataset loaded into Trie..."
```

> **Heads-up:** this compose file names its Redis container `autocomplete-redis`. If you
> already have a standalone container by that name (or something else bound to host port
> 6379), remove it first with `docker rm -f autocomplete-redis`, or use Option B instead.

### Option B — reuse your already-running `redis:7-alpine` container
Use this if you don't want a second Redis instance.

1. Find (or create) a user-defined bridge network that both your existing Redis
   container and the new app container can share, and make sure your Redis container
   is attached to it:
   ```bash
   docker network create redis-net                     # if it doesn't exist yet
   docker network connect redis-net autocomplete-redis  # your redis container's name
   ```
2. Build and start just the app, pointing it at that container by name:
   ```bash
   REDIS_NETWORK=redis-net REDIS_HOST=autocomplete-redis REDIS_PORT=6379 \
     docker compose -f docker-compose.external-redis.yml up --build -d
   ```
   Docker's embedded DNS resolves the container name to the right IP as long as both
   containers share `redis-net`.

Either way, once it's up:
```bash
curl "http://localhost:8080/api/health"
```
should return `{"status":"UP", ...}`.

Tear down with `docker compose down` (or
`docker compose -f docker-compose.external-redis.yml down` for Option B).

### Building without Docker (JDK 17 + Maven locally)
This is the quickest loop if your Redis already publishes port 6379 on the host:
```bash
mvn clean package
REDIS_HOST=localhost REDIS_PORT=6379 java -jar target/autocomplete-system.jar
```

### Running unit tests
```bash
mvn test
```
9 tests cover insert/size, prefix filtering, frequency ordering, top-K truncation,
unknown prefixes, case-insensitivity, and frequency accumulation on duplicate inserts.

---

## 5. Testing the API

### With curl
```bash
curl "http://localhost:8080/api/autocomplete?prefix=app&limit=10"   # first call -> "source":"TRIE"
curl "http://localhost:8080/api/autocomplete?prefix=app&limit=10"   # second call -> "source":"CACHE"
curl "http://localhost:8080/api/autocomplete?prefix=data&limit=5"
curl "http://localhost:8080/api/health"
```

### Verify what landed in Redis
```bash
docker exec -it autocomplete-redis redis-cli KEYS "autocomplete:*"
docker exec -it autocomplete-redis redis-cli GET "autocomplete:app:10"
docker exec -it autocomplete-redis redis-cli TTL "autocomplete:app:10"
```
(If you used Option B, replace `autocomplete-redis` with your own container's name.)
Values are stored as plain JSON, so `GET` output is directly readable. `TTL` counts
down from 300 — the same prefix requested after expiry goes back to the Trie.

To watch a cold run, clear the cache first:
```bash
docker exec -it autocomplete-redis redis-cli --scan --pattern "autocomplete:*" | \
  xargs -r docker exec -i autocomplete-redis redis-cli DEL
```

### With Postman
Import `postman/Autocomplete.postman_collection.json` into Postman (Import → File).
It includes a `baseUrl` variable (defaults to `http://localhost:8080`) and requests for:
health check, a first "app" lookup (expect `TRIE`), a repeat "app" lookup (expect `CACHE`),
a few other prefixes, an unknown prefix (empty result), a missing-parameter request
(expect `400`), and the actuator health endpoint. Each request carries assertions, so
run the whole collection with the Collection Runner and every test should pass.

If you only have a terminal, run the same collection headlessly with **newman**:
```bash
npm install -g newman
newman run postman/Autocomplete.postman_collection.json
```
Run the collection in order — request 3 only reports `CACHE` because request 2 populated
the key first.

---

## 6. Load testing with JMeter (non-GUI / CLI mode)

The test plan (`jmeter/autocomplete_test_plan.jmx`) sends concurrent
`GET /api/autocomplete` requests, cycling through the prefixes in `jmeter/prefixes.csv`,
and asserts every response is `200 OK`. It's parameterized so you can run it purely
from the command line — no JMeter GUI required.

```bash
cd jmeter
jmeter -n -t autocomplete_test_plan.jmx \
  -Jhost=localhost -Jport=8080 -Jthreads=50 -Jrampup=10 -Jloops=20 \
  -l results.jtl \
  -e -o report-output
```

- `-n` non-GUI mode, `-t` the test plan, `-l` raw results file
- `-Jthreads` concurrent virtual users, `-Jrampup` seconds to start them all,
  `-Jloops` iterations per user (default 50 users × 20 loops = 1000 requests,
  cycling through the 20 prefixes in `prefixes.csv`)
- `-Jlimit` top-K per request (default 10), `-Jprefixfile` a different prefix list
- `-e -o report-output` generates an HTML dashboard report you can open in a browser
  (`report-output/index.html`) even though JMeter itself ran headless

Run it from inside the `jmeter/` directory so the relative `prefixes.csv` path resolves,
and delete `results.jtl` / `report-output/` between runs — JMeter refuses to overwrite them.

If JMeter isn't installed, grab it without a GUI installer:
```bash
wget https://dlcdn.apache.org/jmeter/binaries/apache-jmeter-5.6.3.tgz
tar -xzf apache-jmeter-5.6.3.tgz
export PATH="$PWD/apache-jmeter-5.6.3/bin:$PATH"
```

### What to look for
- **Throughput / avg latency** for a *cold* run (flush the cache first, mostly `TRIE`)
  vs. a *second* run right after (mostly `CACHE`) — rerun the same command immediately
  after the first finishes and compare the `report-output/index.html` summaries or the
  `results.jtl` average response times. The cache-warm run should show lower
  average/percentile latency since a single Redis `GET` replaces the Trie DFS + sort.
- **Error %** should stay at 0% — the Response Assertion checks for `200`.
- You can also watch `GET /api/health` before/after a run to see `cacheHits` vs
  `cacheMisses` grow: with 20 prefixes and 1000 requests, a cold run should end at
  roughly 20 misses and 980 hits.

---

## 7. Configuration reference

| Env var             | Default     | Purpose                                  |
|----------------------|-------------|-------------------------------------------|
| `REDIS_HOST`          | `localhost` | Redis hostname                            |
| `REDIS_PORT`          | `6379`      | Redis port                                |
| `CACHE_TTL_SECONDS`   | `300`       | How long cached suggestions live in Redis |
| `SERVER_PORT`         | `8080`      | HTTP port the app listens on              |
| `DATASET_LOCATION`    | `classpath:data/search_terms.csv` | Where the dataset is read from |

---

## 8. Notes / design decisions

- Trie nodes accumulate frequency on duplicate inserts (loading the same term twice
  adds the frequencies rather than overwriting).
- `searchByPrefix` normalizes to lowercase so lookups are case-insensitive.
- If Redis is temporarily unreachable, the service logs a warning and falls back to
  computing directly from the Trie instead of failing the request — the API stays
  available even if the cache layer is down.
- The cache key includes `limit` (`autocomplete:<prefix>:<limit>`) so a request for
  top-5 and a request for top-10 on the same prefix are cached independently.
- Empty results are cached too (negative caching), so repeated typos don't re-walk
  the Trie.

---

## 9. Analysis (lab write-up answers)

**1. Role of each component**
- **Trie** — the prefix index. Terms are stored character-by-character along shared
  paths, so every word that begins with a given prefix lives in one subtree. This is
  what makes "find everything starting with `app`" cheap.
- **Prefix** — the partial query the user has typed so far. It's the lookup key: it
  selects a single node in the Trie, and its subtree is the candidate set.
- **Redis cache** — a cache-aside store for already-computed answers, keyed by
  `autocomplete:<prefix>:<limit>`. Popular prefixes are answered by one Redis `GET`
  instead of a fresh traversal, and the TTL keeps entries from going stale forever.
- **Top-K suggestions** — the ranking/truncation step. A prefix may match hundreds of
  terms; only the K most-searched are useful to the user, so candidates are sorted by
  frequency descending and truncated to K.

**2. How the Trie determines suggestions for a prefix**
Start at the root and consume the prefix one character at a time, following the child
edge for each character. If an edge is missing, no term has that prefix → empty result.
Otherwise you land on the prefix node; every complete word (`endOfWord = true`) in the
subtree below it is a match. A DFS collects those words, accumulating the characters
along the path to reconstruct each term, then the matches are sorted by frequency and
the first K are returned.

**3. Request path on a cache HIT**
`AutocompleteService` builds the key `autocomplete:<prefix>:<limit>` and issues one
Redis `GET`. A non-null value means the answer was computed earlier, so it is
deserialized from JSON and returned immediately — the Trie is never touched, no DFS and
no sort happens, and the response reports `"source": "CACHE"`. Cost is one network
round-trip to Redis, i.e. O(1) with respect to dataset size.

**4. Request path on a cache MISS**
The Redis `GET` returns null. The service then calls `trie.searchByPrefix(prefix, limit)`:
walk to the prefix node, DFS the subtree, sort by frequency, take top-K. That result is
written back to Redis with `SET key value EX 300` so the next identical request is a hit,
and returned with `"source": "TRIE"`. If Redis is down, the read failure is caught, a
warning is logged, and the answer is still computed from the Trie — availability is
preserved and only the caching benefit is lost.

**5. Trie-based prefix search vs. linear search**

| | Linear scan | Trie |
|---|---|---|
| Work per query | Test all N terms with `startsWith` → **O(N × L)** | Walk the prefix (**O(L)**) then visit only the M matching terms → **O(L + M)** |
| Effect of dataset growth | Cost grows with every term added, even irrelevant ones | Cost depends on prefix length and number of matches, not on N |
| Memory | Just the list of terms | Extra pointers per character; shared prefixes are stored once |
| Ranking | Sort the matches | Sort the matches (same step) |

With N = 99 terms the difference is invisible; the Trie's advantage is that response time
stays flat as the term collection grows into the millions, which is exactly the scaling
problem in the problem statement. Adding the Redis cache removes even the O(L + M) work
for repeated prefixes — and in autocomplete traffic, repeats dominate, because every user
typing "system design" passes through `s`, `sy`, `sys`, `syst`, ...

---

## 10. Verification status

Confirmed on this machine (JDK 17.0.20, Maven 3.6.3, Docker Compose v2.39.4, Redis
7-alpine):

- `mvn clean package` — build succeeds, 9/9 Trie unit tests pass.
- App starts and logs `Dataset loaded into Trie: 99 terms ... in 3 ms (0 malformed rows skipped)`.
- `prefix=app&limit=5` → `"source":"TRIE"` on the first call, `"source":"CACHE"` on the second.
- `prefix=zzzz` → `200` with `"count":0`; missing `prefix` → `400`; `/actuator/health` → `200`.
- `/api/health` reports `trieSize: 99` with hit/miss counters incrementing.
- `redis-cli KEYS "autocomplete:*"` shows `autocomplete:app:5`, `GET` returns readable JSON,
  `TTL` reports ~299s.

Not executed here (tooling isn't installed on this machine): the JMeter run and the
`newman` collection run. Their config files are complete and parameterized as documented
above — install JMeter/newman per section 5–6 and the commands work as written.
