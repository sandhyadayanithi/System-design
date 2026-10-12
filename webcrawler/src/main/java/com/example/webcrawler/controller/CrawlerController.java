package com.example.webcrawler.controller;

import com.example.webcrawler.model.CrawlJobDetails;
import com.example.webcrawler.model.CrawlRequest;
import com.example.webcrawler.model.CrawlStatus;
import com.example.webcrawler.model.PagedResponse;
import com.example.webcrawler.service.CrawlerService;
import com.example.webcrawler.service.RedisCrawlStateService;
import com.example.webcrawler.service.UrlValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for managing asynchronous web crawl jobs,
 * monitoring progress, inspecting frontier queues, and querying results.
 */
@RestController
@RequestMapping("/crawler")
public class CrawlerController {

    private final CrawlerService crawlerService;
    private final RedisCrawlStateService stateService;
    private final UrlValidator urlValidator;

    public CrawlerController(CrawlerService crawlerService,
                             RedisCrawlStateService stateService,
                             UrlValidator urlValidator) {
        this.crawlerService = crawlerService;
        this.stateService = stateService;
        this.urlValidator = urlValidator;
    }

    /**
     * POST /crawler/start
     * Accepts either URL parameters or a JSON body.
     */
    @PostMapping("/start")
    public ResponseEntity<?> startCrawling(
            @RequestParam(required = false) String seedUrl,
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false, defaultValue = "50") Integer maxPages,
            @RequestParam(required = false, defaultValue = "3") Integer maxDepth,
            @RequestParam(required = false) String allowedDomain,
            @RequestParam(required = false, defaultValue = "true") Boolean stayOnDomain,
            @RequestBody(required = false) CrawlRequest bodyRequest) {

        CrawlRequest request;
        if (bodyRequest != null) {
            String validationError = bodyRequest.validate();
            if (validationError != null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Bad Request",
                        "message", validationError
                ));
            }

            if (bodyRequest.getSeedUrl() == null || bodyRequest.getSeedUrl().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Bad Request",
                        "message", "seedUrl is required either as a query parameter or in the request body"
                ));
            }

            request = bodyRequest;
            if (request.getMaxPages() == null) request.setMaxPages(CrawlRequest.DEFAULT_MAX_PAGES);
            if (request.getMaxDepth() == null) request.setMaxDepth(CrawlRequest.DEFAULT_MAX_DEPTH);
            if (request.getRequestTimeoutMs() == null) request.setRequestTimeoutMs(CrawlRequest.DEFAULT_REQUEST_TIMEOUT_MS);
            if (request.getPolitenessDelayMs() == null) request.setPolitenessDelayMs(CrawlRequest.DEFAULT_POLITENESS_DELAY_MS);
            if (request.getStayOnDomain() == null) request.setStayOnDomain(CrawlRequest.DEFAULT_STAY_ON_DOMAIN);
        } else {
            if (seedUrl == null || seedUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Bad Request",
                        "message", "seedUrl is required either as a query parameter or in the request body"
                ));
            }
            if (maxPages != null && maxPages <= 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Bad Request",
                        "message", "maxPages must be greater than 0"
                ));
            }
            if (maxDepth != null && maxDepth < 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Bad Request",
                        "message", "maxDepth must be greater than or equal to 0"
                ));
            }
            request = CrawlRequest.builder()
                    .seedUrl(seedUrl.trim())
                    .jobId(jobId)
                    .maxPages(maxPages != null ? maxPages : CrawlRequest.DEFAULT_MAX_PAGES)
                    .maxDepth(maxDepth != null ? maxDepth : CrawlRequest.DEFAULT_MAX_DEPTH)
                    .allowedDomain(allowedDomain)
                    .stayOnDomain(stayOnDomain != null ? stayOnDomain : CrawlRequest.DEFAULT_STAY_ON_DOMAIN)
                    .build();
        }

        if (!urlValidator.isValid(request.getSeedUrl())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid URL",
                    "message", "Seed URL must be a valid HTTP or HTTPS address"
            ));
        }

        String assignedJobId = (request.getJobId() != null && !request.getJobId().trim().isEmpty())
                ? request.getJobId().trim()
                : "job-" + UUID.randomUUID().toString().substring(0, 8);
        request.setJobId(assignedJobId);

        if (stateService.isJobRunning(assignedJobId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Conflict",
                    "message", "Crawl job " + assignedJobId + " is already running"
            ));
        }

        // Start asynchronously
        crawlerService.crawl(request);

        CrawlJobDetails initialDetails = stateService.getJobDetails(assignedJobId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(initialDetails);
    }

    /**
     * GET /crawler/status
     * Returns progress and status of the specified or latest job.
     */
    @GetMapping("/status")
    public ResponseEntity<CrawlJobDetails> getStatus(@RequestParam(required = false) String jobId) {
        String targetJobId = resolveJobId(jobId);
        CrawlJobDetails details = stateService.getJobDetails(targetJobId);
        return ResponseEntity.ok(details);
    }

    /**
     * GET /crawler/jobs/{jobId}
     * Returns full metadata, metrics, and progress for a specific job.
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<CrawlJobDetails> getJob(@PathVariable String jobId) {
        CrawlJobDetails details = stateService.getJobDetails(jobId);
        return ResponseEntity.ok(details);
    }

    /**
     * GET /crawler/jobs/{jobId}/status
     * Returns just the CrawlStatus enum value.
     */
    @GetMapping("/jobs/{jobId}/status")
    public ResponseEntity<Map<String, Object>> getJobStatusOnly(@PathVariable String jobId) {
        CrawlJobDetails details = stateService.getJobDetails(jobId);
        return ResponseEntity.ok(Map.of(
                "jobId", details.getJobId(),
                "status", details.getStatus(),
                "visited", details.getPagesVisited(),
                "discovered", details.getPagesDiscovered(),
                "durationMs", details.getDurationMs() != null ? details.getDurationMs() : 0
        ));
    }

    /**
     * GET /crawler/jobs
     * Lists all registered job IDs.
     */
    @GetMapping("/jobs")
    public ResponseEntity<Map<String, Object>> listJobs() {
        Set<String> jobs = stateService.listAllJobs();
        return ResponseEntity.ok(Map.of(
                "count", jobs.size(),
                "latestJobId", stateService.getLatestJobId(),
                "jobs", jobs
        ));
    }

    /**
     * GET /crawler/visited
     * Paginated list of visited URLs.
     */
    @GetMapping("/visited")
    public ResponseEntity<?> getVisitedUrls(
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {

        String targetJobId = resolveJobId(jobId);
        List<String> visited = stateService.getVisitedUrls(targetJobId);

        // If pagination parameters are default and size == 0, return simple list for backward compatibility
        if (size <= 0) {
            return ResponseEntity.ok(visited);
        }
        return ResponseEntity.ok(PagedResponse.of(visited, page, size));
    }

    /**
     * GET /crawler/discovered
     * Paginated list of discovered URLs.
     */
    @GetMapping("/discovered")
    public ResponseEntity<?> getDiscoveredUrls(
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {

        String targetJobId = resolveJobId(jobId);
        List<String> discovered = stateService.getDiscoveredUrls(targetJobId);

        if (size <= 0) {
            return ResponseEntity.ok(discovered);
        }
        return ResponseEntity.ok(PagedResponse.of(discovered, page, size));
    }

    /**
     * GET /crawler/queue
     * Paginated list of pending URLs waiting in the frontier queue.
     */
    @GetMapping("/queue")
    public ResponseEntity<?> getQueueUrls(
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {

        String targetJobId = resolveJobId(jobId);
        List<String> queue = stateService.getQueueUrls(targetJobId);

        if (size <= 0) {
            return ResponseEntity.ok(queue);
        }
        return ResponseEntity.ok(PagedResponse.of(queue, page, size));
    }

    /**
     * GET /crawler/failed
     * Map of URLs that failed during retrieval and their error reasons.
     */
    @GetMapping("/failed")
    public ResponseEntity<Map<String, Object>> getFailedUrls(@RequestParam(required = false) String jobId) {
        String targetJobId = resolveJobId(jobId);
        Map<String, String> failed = stateService.getFailedUrls(targetJobId);
        return ResponseEntity.ok(Map.of(
                "jobId", targetJobId,
                "failedCount", failed.size(),
                "failures", failed
        ));
    }

    /**
     * DELETE /crawler/jobs/{jobId}/state
     * Clears all state and Redis keys associated with a specific job.
     */
    @DeleteMapping("/jobs/{jobId}/state")
    public ResponseEntity<Map<String, String>> clearJobState(@PathVariable String jobId) {
        stateService.clearJobState(jobId);
        return ResponseEntity.ok(Map.of(
                "message", "State cleared for job " + jobId,
                "jobId", jobId
        ));
    }

    /**
     * Legacy DELETE endpoints for backward compatibility.
     */
    @DeleteMapping("/visited")
    public ResponseEntity<Map<String, String>> clearVisitedUrls() {
        stateService.clearJobState(stateService.getLatestJobId());
        return ResponseEntity.ok(Map.of("message", "Visited URL set cleared"));
    }

    @DeleteMapping("/state")
    public ResponseEntity<Map<String, String>> clearCrawlerState() {
        stateService.clearJobState(stateService.getLatestJobId());
        return ResponseEntity.ok(Map.of("message", "Crawler state cleared"));
    }

    /**
     * GET /crawler/health
     * Health check endpoint confirming crawler and Redis connectivity.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean redisUp = stateService.isRedisAvailable();
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "redisConnected", redisUp,
                "activeJobs", stateService.listAllJobs().size(),
                "latestJobId", stateService.getLatestJobId()
        ));
    }

    private String resolveJobId(String jobId) {
        return (jobId != null && !jobId.trim().isEmpty()) ? jobId.trim() : stateService.getLatestJobId();
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        String message = "Invalid request payload or malformed field";
        Throwable root = ex.getMostSpecificCause();
        if (root != null && root.getMessage() != null) {
            String raw = root.getMessage();
            int idx = raw.indexOf('\n');
            if (idx > 0) raw = raw.substring(0, idx);
            message = raw.trim();
        }
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request",
                "message", message,
                "status", 400
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request",
                "message", ex.getMessage(),
                "status", 400
        ));
    }
}