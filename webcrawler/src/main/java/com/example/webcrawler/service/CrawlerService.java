package com.example.webcrawler.service;

import com.example.webcrawler.model.CrawlJobDetails;
import com.example.webcrawler.model.CrawlRequest;
import com.example.webcrawler.model.CrawlStatus;
import com.example.webcrawler.model.CrawlTask;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.UUID;

/**
 * Asynchronous web content discovery engine with depth tracking, domain boundaries,
 * Redis atomic deduplication, and failure resilience.
 */
@Service
public class CrawlerService {

    private static final Logger log = LoggerFactory.getLogger(CrawlerService.class);

    private final RedisCrawlStateService stateService;
    private final UrlValidator urlValidator;

    public CrawlerService(RedisCrawlStateService stateService, UrlValidator urlValidator) {
        this.stateService = stateService;
        this.urlValidator = urlValidator;
    }

    /**
     * Legacy entry point for backward compatibility.
     */
    @Async
    public void crawl(String seedUrl) {
        CrawlRequest request = CrawlRequest.builder()
                .seedUrl(seedUrl)
                .jobId("default")
                .maxPages(50)
                .maxDepth(3)
                .stayOnDomain(true)
                .build();
        crawl(request);
    }

    /**
     * Executes an asynchronous crawl job using the specified configuration.
     */
    @Async
    public void crawl(CrawlRequest request) {
        String seedUrl = request.getSeedUrl();
        if (seedUrl == null || !urlValidator.isValid(seedUrl)) {
            log.error("Invalid seed URL provided: {}", seedUrl);
            String jobId = request.getJobId() != null ? request.getJobId() : "job-" + UUID.randomUUID().toString().substring(0, 8);
            stateService.initJob(jobId, request);
            stateService.setStatus(jobId, CrawlStatus.FAILED, "Invalid seed URL format or unsupported scheme");
            return;
        }

        String normalizedSeed = urlValidator.normalize(seedUrl);
        String jobId = (request.getJobId() != null && !request.getJobId().trim().isEmpty())
                ? request.getJobId().trim()
                : "job-" + UUID.randomUUID().toString().substring(0, 8);
        request.setJobId(jobId);

        // Auto-configure allowedDomain if stayOnDomain is active
        if ((request.getAllowedDomain() == null || request.getAllowedDomain().trim().isEmpty()) && request.isStayOnDomain()) {
            request.setAllowedDomain(urlValidator.extractHost(normalizedSeed));
        }

        if (stateService.isJobRunning(jobId)) {
            log.warn("Crawl job {} is already running; skipping invocation", jobId);
            return;
        }

        log.info("Starting crawl job [{}]: seed={}, maxPages={}, maxDepth={}, domain={}",
                jobId, normalizedSeed, request.getMaxPages(), request.getMaxDepth(), request.getAllowedDomain());

        stateService.initJob(jobId, request);
        stateService.markDiscoveredAtomic(jobId, normalizedSeed);
        stateService.enqueue(jobId, new CrawlTask(normalizedSeed, 0, null));

        int visitedCount = 0;

        try {
            while (visitedCount < request.getMaxPages()) {
                CrawlTask task = stateService.dequeue(jobId);
                if (task == null) {
                    // Queue exhausted, all discovered links processed
                    break;
                }

                String currentUrl = task.getUrl();
                int currentDepth = task.getDepth();

                // Check depth limit
                if (currentDepth > request.getMaxDepth()) {
                    continue;
                }

                // Check if already visited (atomic SADD)
                if (!stateService.markVisitedAtomic(jobId, currentUrl)) {
                    continue; // Already processed by another task
                }

                visitedCount++;

                // Politeness delay
                if (request.getPolitenessDelayMs() > 0) {
                    try {
                        Thread.sleep(request.getPolitenessDelayMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Crawl job {} interrupted", jobId);
                        stateService.setStatus(jobId, CrawlStatus.STOPPED, "Interrupted");
                        return;
                    }
                }

                // Fetch and parse HTML content
                try {
                    log.debug("[{}] Fetching (depth {}): {}", jobId, currentDepth, currentUrl);

                    Document document = Jsoup.connect(currentUrl)
                            .timeout(request.getRequestTimeoutMs())
                            .userAgent("Mozilla/5.0 (compatible; SystemDesignWebCrawler/2.0)")
                            .followRedirects(true)
                            .get();

                    Elements links = document.select("a[href]");
                    for (Element link : links) {
                        String rawDiscovered = link.absUrl("href");
                        if (!urlValidator.isValid(rawDiscovered)) {
                            continue;
                        }

                        String normalizedChild = urlValidator.normalize(rawDiscovered);
                        if (normalizedChild == null) {
                            continue;
                        }

                        // Domain boundary enforcement
                        if (!urlValidator.isDomainAllowed(normalizedChild, request.getAllowedDomain())) {
                            continue;
                        }

                        int nextDepth = currentDepth + 1;

                        // Atomic discovery deduplication via Redis SADD
                        boolean newlyDiscovered = stateService.markDiscoveredAtomic(jobId, normalizedChild);

                        // Enqueue if newly discovered and within configured depth limit
                        if (newlyDiscovered && nextDepth <= request.getMaxDepth()) {
                            if (!stateService.isVisited(jobId, normalizedChild)) {
                                stateService.enqueue(jobId, new CrawlTask(normalizedChild, nextDepth, currentUrl));
                            }
                        }
                    }

                } catch (HttpStatusException hse) {
                    log.warn("[{}] HTTP {} on {}", jobId, hse.getStatusCode(), currentUrl);
                    stateService.markFailed(jobId, currentUrl, "HTTP " + hse.getStatusCode());
                } catch (SocketTimeoutException ste) {
                    log.warn("[{}] Timeout fetching {}", jobId, currentUrl);
                    stateService.markFailed(jobId, currentUrl, "Socket timeout (" + request.getRequestTimeoutMs() + "ms)");
                } catch (Exception ex) {
                    log.warn("[{}] Failed to fetch {}: {}", jobId, currentUrl, ex.getMessage());
                    stateService.markFailed(jobId, currentUrl, ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
            }

            // Determine final status
            if (visitedCount == 0 && stateService.getFailedCount(jobId) > 0) {
                stateService.setStatus(jobId, CrawlStatus.FAILED, "Seed page could not be reached");
            } else {
                stateService.setStatus(jobId, CrawlStatus.COMPLETED,
                        String.format("Crawled %d pages, discovered %d URLs", visitedCount, stateService.getDiscoveredCount(jobId)));
            }

            log.info("Finished crawl job [{}]: visited={}, discovered={}, failed={}",
                    jobId, visitedCount, stateService.getDiscoveredCount(jobId), stateService.getFailedCount(jobId));

        } catch (Exception e) {
            log.error("Fatal error during crawl job {}", jobId, e);
            stateService.setStatus(jobId, CrawlStatus.FAILED, e.getMessage());
        }
    }

    /**
     * Legacy status check.
     */
    public CrawlStatus getStatus() {
        return getStatus(null);
    }

    public CrawlStatus getStatus(String jobId) {
        CrawlJobDetails details = stateService.getJobDetails(jobId);
        return details != null ? details.getStatus() : CrawlStatus.IDLE;
    }
}