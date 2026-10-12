package com.example.webcrawler.service;

import com.example.webcrawler.model.CrawlJobDetails;
import com.example.webcrawler.model.CrawlRequest;
import com.example.webcrawler.model.CrawlStatus;
import com.example.webcrawler.model.CrawlTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages shared, job-isolated crawling state using Redis with in-memory fallback.
 */
@Service
public class RedisCrawlStateService {

    private static final Logger log = LoggerFactory.getLogger(RedisCrawlStateService.class);

    private static final String KEY_ALL_JOBS = "crawler:jobs";
    private static final String KEY_ACTIVE_JOBS = "crawler:active_jobs";
    private static final String KEY_LATEST_JOB = "crawler:latest_job";
    private static final String DELIMITER = "|||";

    private final RedisTemplate<String, String> redisTemplate;

    // Fallback in-memory stores in case Redis is unreachable
    private final Map<String, CrawlJobDetails> memoryJobs = new ConcurrentHashMap<>();
    private final Map<String, Queue<CrawlTask>> memoryQueues = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> memoryVisited = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> memoryDiscovered = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> memoryFailed = new ConcurrentHashMap<>();
    private volatile String latestJobId = "default";

    public RedisCrawlStateService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String keyMeta(String jobId) {
        return "crawler:job:" + jobId + ":meta";
    }

    private String keyQueue(String jobId) {
        return "crawler:job:" + jobId + ":queue";
    }

    private String keyVisited(String jobId) {
        return "crawler:job:" + jobId + ":visited";
    }

    private String keyDiscovered(String jobId) {
        return "crawler:job:" + jobId + ":discovered";
    }

    private String keyFailed(String jobId) {
        return "crawler:job:" + jobId + ":failed";
    }

    private String serializeTask(CrawlTask task) {
        return task.getUrl() + DELIMITER + task.getDepth() + DELIMITER +
                (task.getParentUrl() != null ? task.getParentUrl() : "") + DELIMITER +
                task.getDiscoveredTime();
    }

    private CrawlTask deserializeTask(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String[] parts = raw.split("\\|\\|\\|", -1);
        if (parts.length < 2) {
            return new CrawlTask(raw, 0, null);
        }
        String url = parts[0];
        int depth = 0;
        try {
            depth = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ignored) {}

        String parent = parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null;
        long time = System.currentTimeMillis();
        if (parts.length > 3 && !parts[3].isEmpty()) {
            try {
                time = Long.parseLong(parts[3]);
            } catch (NumberFormatException ignored) {}
        }
        return new CrawlTask(url, depth, parent, time);
    }

    /**
     * Checks if Redis is connected and responsive.
     */
    public boolean isRedisAvailable() {
        try {
            String ping = redisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equalsIgnoreCase(ping);
        } catch (Exception e) {
            log.warn("Redis is not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Initializes a new crawl job metadata and records it in Redis.
     */
    public void initJob(String jobId, CrawlRequest request) {
        latestJobId = jobId;
        CrawlJobDetails details = CrawlJobDetails.builder()
                .jobId(jobId)
                .seedUrl(request.getSeedUrl())
                .status(CrawlStatus.RUNNING)
                .maxPages(request.getMaxPages())
                .maxDepth(request.getMaxDepth())
                .allowedDomain(request.getAllowedDomain())
                .pagesVisited(0)
                .pagesDiscovered(0)
                .pagesFailed(0)
                .queueSize(0)
                .startTime(System.currentTimeMillis())
                .message("Crawl job initiated")
                .build();

        memoryJobs.put(jobId, details);
        memoryQueues.put(jobId, new ConcurrentLinkedQueue<>());
        memoryVisited.put(jobId, ConcurrentHashMap.newKeySet());
        memoryDiscovered.put(jobId, ConcurrentHashMap.newKeySet());
        memoryFailed.put(jobId, new ConcurrentHashMap<>());

        try {
            redisTemplate.opsForSet().add(KEY_ALL_JOJobs(jobId));
            redisTemplate.opsForSet().add(KEY_ACTIVE_JOBS, jobId);
            redisTemplate.opsForValue().set(KEY_LATEST_JOB, jobId);

            Map<String, String> meta = new HashMap<>();
            meta.put("jobId", jobId);
            meta.put("seedUrl", request.getSeedUrl());
            meta.put("status", CrawlStatus.RUNNING.name());
            meta.put("maxPages", String.valueOf(request.getMaxPages()));
            meta.put("maxDepth", String.valueOf(request.getMaxDepth()));
            meta.put("allowedDomain", request.getAllowedDomain() != null ? request.getAllowedDomain() : "");
            meta.put("startTime", String.valueOf(details.getStartTime()));
            meta.put("message", details.getMessage());

            redisTemplate.opsForHash().putAll(keyMeta(jobId), meta);
        } catch (Exception e) {
            log.warn("Failed to write job init to Redis (falling back to memory): {}", e.getMessage());
        }
    }

    private String KEY_ALL_JOJobs(String jobId) {
        return KEY_ALL_JOBS;
    }

    /**
     * Updates job status, completion timestamps, and error messages.
     */
    public void setStatus(String jobId, CrawlStatus status, String message) {
        long now = System.currentTimeMillis();
        CrawlJobDetails mem = memoryJobs.get(jobId);
        if (mem != null) {
            mem.setStatus(status);
            if (message != null) mem.setMessage(message);
            if (status == CrawlStatus.COMPLETED || status == CrawlStatus.FAILED || status == CrawlStatus.STOPPED) {
                mem.setEndTime(now);
                if (mem.getStartTime() != null) {
                    mem.setDurationMs(now - mem.getStartTime());
                }
            }
        }

        try {
            redisTemplate.opsForHash().put(keyMeta(jobId), "status", status.name());
            if (message != null) {
                redisTemplate.opsForHash().put(keyMeta(jobId), "message", message);
            }
            if (status == CrawlStatus.COMPLETED || status == CrawlStatus.FAILED || status == CrawlStatus.STOPPED) {
                redisTemplate.opsForSet().remove(KEY_ACTIVE_JOBS, jobId);
                redisTemplate.opsForHash().put(keyMeta(jobId), "endTime", String.valueOf(now));
                if (mem != null && mem.getDurationMs() != null) {
                    redisTemplate.opsForHash().put(keyMeta(jobId), "durationMs", String.valueOf(mem.getDurationMs()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to update status in Redis for job {}: {}", jobId, e.getMessage());
        }
    }

    /**
     * Atomic deduplication on discovery using Redis SADD.
     * Returns true if URL was freshly added; false if already discovered.
     */
    public boolean markDiscoveredAtomic(String jobId, String url) {
        Set<String> memSet = memoryDiscovered.computeIfAbsent(jobId, k -> ConcurrentHashMap.newKeySet());
        boolean memAdded = memSet.add(url);

        try {
            Long res = redisTemplate.opsForSet().add(keyDiscovered(jobId), url);
            return res != null && res > 0;
        } catch (Exception e) {
            log.debug("Redis unavailable on markDiscoveredAtomic: {}", e.getMessage());
            return memAdded;
        }
    }

    /**
     * Atomic deduplication on visit using Redis SADD.
     * Returns true if URL was not previously marked visited.
     */
    public boolean markVisitedAtomic(String jobId, String url) {
        Set<String> memSet = memoryVisited.computeIfAbsent(jobId, k -> ConcurrentHashMap.newKeySet());
        boolean memAdded = memSet.add(url);

        try {
            Long res = redisTemplate.opsForSet().add(keyVisited(jobId), url);
            return res != null && res > 0;
        } catch (Exception e) {
            log.debug("Redis unavailable on markVisitedAtomic: {}", e.getMessage());
            return memAdded;
        }
    }

    /**
     * Checks if a URL has already been visited.
     */
    public boolean isVisited(String jobId, String url) {
        try {
            Boolean member = redisTemplate.opsForSet().isMember(keyVisited(jobId), url);
            if (member != null) return member;
        } catch (Exception e) {
            // fallback
        }
        Set<String> mem = memoryVisited.get(jobId);
        return mem != null && mem.contains(url);
    }

    /**
     * Adds an item to the frontier queue.
     */
    public void enqueue(String jobId, CrawlTask task) {
        Queue<CrawlTask> q = memoryQueues.computeIfAbsent(jobId, k -> new ConcurrentLinkedQueue<>());
        q.offer(task);

        try {
            String encoded = serializeTask(task);
            redisTemplate.opsForList().rightPush(keyQueue(jobId), encoded);
        } catch (Exception e) {
            log.debug("Redis unavailable on enqueue: {}", e.getMessage());
        }
    }

    /**
     * Dequeues the next item from the frontier queue.
     */
    public CrawlTask dequeue(String jobId) {
        try {
            String encoded = redisTemplate.opsForList().leftPop(keyQueue(jobId));
            if (encoded != null) {
                return deserializeTask(encoded);
            }
        } catch (Exception e) {
            log.debug("Redis unavailable on dequeue: {}", e.getMessage());
        }

        Queue<CrawlTask> q = memoryQueues.get(jobId);
        return q != null ? q.poll() : null;
    }

    /**
     * Records a failed page fetch with error reason.
     */
    public void markFailed(String jobId, String url, String reason) {
        Map<String, String> mem = memoryFailed.computeIfAbsent(jobId, k -> new ConcurrentHashMap<>());
        mem.put(url, reason);

        try {
            redisTemplate.opsForHash().put(keyFailed(jobId), url, reason);
        } catch (Exception e) {
            log.debug("Redis unavailable on markFailed: {}", e.getMessage());
        }
    }

    /**
     * Retrieves full details and live counts for a job.
     */
    public CrawlJobDetails getJobDetails(String jobId) {
        String targetJobId = (jobId == null || jobId.trim().isEmpty() || "latest".equalsIgnoreCase(jobId))
                ? getLatestJobId()
                : jobId.trim();

        int visitedCount = getVisitedCount(targetJobId);
        int discoveredCount = getDiscoveredCount(targetJobId);
        int failedCount = getFailedCount(targetJobId);
        int queueCount = getQueueSize(targetJobId);

        CrawlJobDetails details = memoryJobs.get(targetJobId);
        if (details == null) {
            details = CrawlJobDetails.builder().jobId(targetJobId).status(CrawlStatus.IDLE).build();
        }

        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(keyMeta(targetJobId));
            if (!entries.isEmpty()) {
                String statusStr = (String) entries.get("status");
                CrawlStatus status = statusStr != null ? CrawlStatus.valueOf(statusStr) : CrawlStatus.IDLE;
                String seed = (String) entries.get("seedUrl");
                String maxP = (String) entries.get("maxPages");
                String maxD = (String) entries.get("maxDepth");
                String domain = (String) entries.get("allowedDomain");
                String startT = (String) entries.get("startTime");
                String endT = (String) entries.get("endTime");
                String duration = (String) entries.get("durationMs");
                String msg = (String) entries.get("message");

                return CrawlJobDetails.builder()
                        .jobId(targetJobId)
                        .seedUrl(seed)
                        .status(status)
                        .maxPages(maxP != null ? Integer.parseInt(maxP) : details.getMaxPages())
                        .maxDepth(maxD != null ? Integer.parseInt(maxD) : details.getMaxDepth())
                        .allowedDomain(domain)
                        .pagesVisited(visitedCount)
                        .pagesDiscovered(discoveredCount)
                        .pagesFailed(failedCount)
                        .queueSize(queueCount)
                        .startTime(startT != null ? Long.parseLong(startT) : details.getStartTime())
                        .endTime(endT != null ? Long.parseLong(endT) : details.getEndTime())
                        .durationMs(duration != null ? Long.parseLong(duration) : details.getDurationMs())
                        .message(msg)
                        .build();
            }
        } catch (Exception e) {
            log.debug("Redis unavailable on getJobDetails: {}", e.getMessage());
        }

        details.setPagesVisited(visitedCount);
        details.setPagesDiscovered(discoveredCount);
        details.setPagesFailed(failedCount);
        details.setQueueSize(queueCount);
        return details;
    }

    public int getQueueSize(String jobId) {
        try {
            Long size = redisTemplate.opsForList().size(keyQueue(jobId));
            if (size != null) return size.intValue();
        } catch (Exception e) {}
        Queue<CrawlTask> q = memoryQueues.get(jobId);
        return q != null ? q.size() : 0;
    }

    public int getVisitedCount(String jobId) {
        try {
            Long size = redisTemplate.opsForSet().size(keyVisited(jobId));
            if (size != null) return size.intValue();
        } catch (Exception e) {}
        Set<String> s = memoryVisited.get(jobId);
        return s != null ? s.size() : 0;
    }

    public int getDiscoveredCount(String jobId) {
        try {
            Long size = redisTemplate.opsForSet().size(keyDiscovered(jobId));
            if (size != null) return size.intValue();
        } catch (Exception e) {}
        Set<String> s = memoryDiscovered.get(jobId);
        return s != null ? s.size() : 0;
    }

    public int getFailedCount(String jobId) {
        try {
            Long size = redisTemplate.opsForHash().size(keyFailed(jobId));
            if (size != null) return size.intValue();
        } catch (Exception e) {}
        Map<String, String> m = memoryFailed.get(jobId);
        return m != null ? m.size() : 0;
    }

    public List<String> getVisitedUrls(String jobId) {
        try {
            Set<String> members = redisTemplate.opsForSet().members(keyVisited(jobId));
            if (members != null && !members.isEmpty()) {
                return new ArrayList<>(members);
            }
        } catch (Exception e) {}
        Set<String> mem = memoryVisited.get(jobId);
        return mem != null ? new ArrayList<>(mem) : Collections.emptyList();
    }

    public List<String> getDiscoveredUrls(String jobId) {
        try {
            Set<String> members = redisTemplate.opsForSet().members(keyDiscovered(jobId));
            if (members != null && !members.isEmpty()) {
                return new ArrayList<>(members);
            }
        } catch (Exception e) {}
        Set<String> mem = memoryDiscovered.get(jobId);
        return mem != null ? new ArrayList<>(mem) : Collections.emptyList();
    }

    public List<String> getQueueUrls(String jobId) {
        List<String> result = new ArrayList<>();
        try {
            List<String> raw = redisTemplate.opsForList().range(keyQueue(jobId), 0, -1);
            if (raw != null) {
                for (String s : raw) {
                    CrawlTask task = deserializeTask(s);
                    if (task != null) {
                        result.add(task.getUrl() + " (depth: " + task.getDepth() + ")");
                    } else {
                        result.add(s);
                    }
                }
                return result;
            }
        } catch (Exception e) {}

        Queue<CrawlTask> q = memoryQueues.get(jobId);
        if (q != null) {
            for (CrawlTask t : q) {
                result.add(t.getUrl() + " (depth: " + t.getDepth() + ")");
            }
        }
        return result;
    }

    public Map<String, String> getFailedUrls(String jobId) {
        Map<String, String> result = new HashMap<>();
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(keyFailed(jobId));
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
            if (!result.isEmpty()) return result;
        } catch (Exception e) {}

        Map<String, String> mem = memoryFailed.get(jobId);
        return mem != null ? new HashMap<>(mem) : Collections.emptyMap();
    }

    public Set<String> listAllJobs() {
        Set<String> jobs = new HashSet<>();
        try {
            Set<String> redisJobs = redisTemplate.opsForSet().members(KEY_ALL_JOBS);
            if (redisJobs != null) jobs.addAll(redisJobs);
        } catch (Exception e) {}
        jobs.addAll(memoryJobs.keySet());
        return jobs;
    }

    public boolean isJobRunning(String jobId) {
        try {
            Boolean isMember = redisTemplate.opsForSet().isMember(KEY_ACTIVE_JOBS, jobId);
            if (Boolean.TRUE.equals(isMember)) return true;
        } catch (Exception e) {}

        CrawlJobDetails d = memoryJobs.get(jobId);
        return d != null && d.getStatus() == CrawlStatus.RUNNING;
    }

    public void clearJobState(String jobId) {
        memoryJobs.remove(jobId);
        memoryQueues.remove(jobId);
        memoryVisited.remove(jobId);
        memoryDiscovered.remove(jobId);
        memoryFailed.remove(jobId);

        try {
            redisTemplate.delete(Arrays.asList(
                    keyMeta(jobId),
                    keyQueue(jobId),
                    keyVisited(jobId),
                    keyDiscovered(jobId),
                    keyFailed(jobId)
            ));
            redisTemplate.opsForSet().remove(KEY_ALL_JOBS, jobId);
            redisTemplate.opsForSet().remove(KEY_ACTIVE_JOBS, jobId);
        } catch (Exception e) {
            log.warn("Failed to clear Redis state for job {}: {}", jobId, e.getMessage());
        }
    }

    public String getLatestJobId() {
        try {
            String id = redisTemplate.opsForValue().get(KEY_LATEST_JOB);
            if (id != null && !id.trim().isEmpty()) return id;
        } catch (Exception e) {}
        return latestJobId;
    }
}
