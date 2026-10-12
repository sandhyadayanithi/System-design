package com.example.webcrawler.controller;

import com.example.webcrawler.model.CrawlJobDetails;
import com.example.webcrawler.model.CrawlRequest;
import com.example.webcrawler.model.CrawlStatus;
import com.example.webcrawler.service.CrawlerService;
import com.example.webcrawler.service.RedisCrawlStateService;
import com.example.webcrawler.service.UrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CrawlerControllerTest {

    private CrawlerService crawlerService;
    private RedisCrawlStateService stateService;
    private UrlValidator urlValidator;
    private CrawlerController controller;

    @BeforeEach
    void setUp() {
        crawlerService = mock(CrawlerService.class);
        stateService = mock(RedisCrawlStateService.class);
        urlValidator = new UrlValidator();
        controller = new CrawlerController(crawlerService, stateService, urlValidator);
    }

    @Test
    @DisplayName("POST /crawler/start with valid seedUrl should accept job")
    void testStartJobSuccess() {
        when(stateService.isJobRunning(anyString())).thenReturn(false);
        when(stateService.getJobDetails(anyString())).thenReturn(
                CrawlJobDetails.builder()
                        .jobId("test-job-1")
                        .seedUrl("http://localhost:9000/page1.html")
                        .status(CrawlStatus.RUNNING)
                        .build()
        );

        ResponseEntity<?> response = controller.startCrawling(
                "http://localhost:9000/page1.html", "test-job-1", 10, 2, "localhost", true, null
        );

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(crawlerService, times(1)).crawl(any(CrawlRequest.class));
    }

    @Test
    @DisplayName("POST /crawler/start without seedUrl should return 400 Bad Request")
    void testStartJobMissingSeed() {
        ResponseEntity<?> response = controller.startCrawling(null, null, 10, 2, null, true, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(crawlerService, never()).crawl(any(CrawlRequest.class));
    }

    @Test
    @DisplayName("POST /crawler/start with invalid seed URL should return 400 Bad Request")
    void testStartJobInvalidUrl() {
        ResponseEntity<?> response = controller.startCrawling("not-a-valid-url", null, 10, 2, null, true, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(crawlerService, never()).crawl(any(CrawlRequest.class));
    }

    @Test
    @DisplayName("POST /crawler/start on already running job should return 409 Conflict")
    void testStartJobConflict() {
        when(stateService.isJobRunning("job-conflict")).thenReturn(true);

        ResponseEntity<?> response = controller.startCrawling(
                "http://localhost:9000/page1.html", "job-conflict", 10, 2, null, true, null
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(crawlerService, never()).crawl(any(CrawlRequest.class));
    }

    @Test
    @DisplayName("GET /crawler/health should report status and Redis connectivity")
    void testHealthCheck() {
        when(stateService.isRedisAvailable()).thenReturn(true);
        when(stateService.getLatestJobId()).thenReturn("job-123");

        ResponseEntity<Map<String, Object>> response = controller.health();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals(true, response.getBody().get("redisConnected"));
    }

    @Test
    @DisplayName("GET /crawler/visited should return paginated visited list")
    void testGetVisitedPagination() {
        when(stateService.getLatestJobId()).thenReturn("job-1");
        when(stateService.getVisitedUrls("job-1")).thenReturn(List.of(
                "http://localhost:9000/page1.html",
                "http://localhost:9000/page2.html"
        ));

        ResponseEntity<?> response = controller.getVisitedUrls("job-1", 0, 10);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("DELETE /crawler/jobs/{jobId}/state should invoke state clearing")
    void testClearJobState() {
        ResponseEntity<Map<String, String>> response = controller.clearJobState("job-to-delete");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(stateService, times(1)).clearJobState("job-to-delete");
    }
}
