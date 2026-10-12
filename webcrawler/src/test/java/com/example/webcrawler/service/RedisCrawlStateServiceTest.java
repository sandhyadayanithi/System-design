package com.example.webcrawler.service;

import com.example.webcrawler.model.CrawlJobDetails;
import com.example.webcrawler.model.CrawlRequest;
import com.example.webcrawler.model.CrawlStatus;
import com.example.webcrawler.model.CrawlTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RedisCrawlStateServiceTest {

    private RedisCrawlStateService stateService;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> mockTemplate = Mockito.mock(RedisTemplate.class);
        // By using mock template with null returns, stateService operates via its built-in in-memory fallback
        stateService = new RedisCrawlStateService(mockTemplate);
    }

    @Test
    @DisplayName("Should atomically deduplicate discovered URLs")
    void testDiscoveredAtomicDeduplication() {
        String jobId = "job-dedup-test";
        String url = "http://localhost:9000/page1.html";

        // First attempt must return true (fresh discovery)
        assertTrue(stateService.markDiscoveredAtomic(jobId, url));

        // Second attempt must return false (duplicate detection)
        assertFalse(stateService.markDiscoveredAtomic(jobId, url));

        assertEquals(1, stateService.getDiscoveredCount(jobId));
    }

    @Test
    @DisplayName("Should atomically deduplicate visited URLs")
    void testVisitedAtomicDeduplication() {
        String jobId = "job-visit-test";
        String url = "http://localhost:9000/page1.html";

        assertFalse(stateService.isVisited(jobId, url));
        assertTrue(stateService.markVisitedAtomic(jobId, url));
        assertTrue(stateService.isVisited(jobId, url));

        // Second visit attempt returns false
        assertFalse(stateService.markVisitedAtomic(jobId, url));
        assertEquals(1, stateService.getVisitedCount(jobId));
    }

    @Test
    @DisplayName("Should properly manage FIFO frontier queue with depth metadata")
    void testQueueOperations() {
        String jobId = "job-queue-test";

        CrawlTask task1 = new CrawlTask("http://localhost:9000/page1.html", 0, null);
        CrawlTask task2 = new CrawlTask("http://localhost:9000/page2.html", 1, "http://localhost:9000/page1.html");

        stateService.enqueue(jobId, task1);
        stateService.enqueue(jobId, task2);

        assertEquals(2, stateService.getQueueSize(jobId));

        CrawlTask popped1 = stateService.dequeue(jobId);
        assertNotNull(popped1);
        assertEquals("http://localhost:9000/page1.html", popped1.getUrl());
        assertEquals(0, popped1.getDepth());

        CrawlTask popped2 = stateService.dequeue(jobId);
        assertNotNull(popped2);
        assertEquals("http://localhost:9000/page2.html", popped2.getUrl());
        assertEquals(1, popped2.getDepth());
        assertEquals("http://localhost:9000/page1.html", popped2.getParentUrl());

        assertNull(stateService.dequeue(jobId));
        assertEquals(0, stateService.getQueueSize(jobId));
    }

    @Test
    @DisplayName("Should maintain complete isolation between different crawl jobs")
    void testJobIsolation() {
        String jobA = "job-alpha";
        String jobB = "job-beta";

        stateService.initJob(jobA, CrawlRequest.builder().seedUrl("http://site-a.com").build());
        stateService.initJob(jobB, CrawlRequest.builder().seedUrl("http://site-b.com").build());

        stateService.markVisitedAtomic(jobA, "http://site-a.com/page1");
        stateService.markVisitedAtomic(jobB, "http://site-b.com/page1");

        List<String> visitedA = stateService.getVisitedUrls(jobA);
        List<String> visitedB = stateService.getVisitedUrls(jobB);

        assertEquals(1, visitedA.size());
        assertEquals(1, visitedB.size());
        assertTrue(visitedA.contains("http://site-a.com/page1"));
        assertFalse(visitedA.contains("http://site-b.com/page1"));
        assertTrue(visitedB.contains("http://site-b.com/page1"));

        // Clear job A should not affect job B
        stateService.clearJobState(jobA);
        assertEquals(0, stateService.getVisitedCount(jobA));
        assertEquals(1, stateService.getVisitedCount(jobB));
    }

    @Test
    @DisplayName("Should track job lifecycle and error status transitions")
    void testJobLifecycle() {
        String jobId = "job-lifecycle";
        CrawlRequest req = CrawlRequest.builder()
                .seedUrl("http://test.com")
                .maxPages(10)
                .maxDepth(2)
                .build();

        stateService.initJob(jobId, req);
        CrawlJobDetails details1 = stateService.getJobDetails(jobId);
        assertEquals(CrawlStatus.RUNNING, details1.getStatus());

        stateService.markFailed(jobId, "http://test.com/broken", "HTTP 404");
        assertEquals(1, stateService.getFailedCount(jobId));

        stateService.setStatus(jobId, CrawlStatus.COMPLETED, "Done");
        CrawlJobDetails details2 = stateService.getJobDetails(jobId);
        assertEquals(CrawlStatus.COMPLETED, details2.getStatus());
        assertNotNull(details2.getEndTime());
        assertNotNull(details2.getDurationMs());
    }
}
