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
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CrawlerControllerTest {

    private CrawlerService crawlerService;
    private RedisCrawlStateService stateService;
    private UrlValidator urlValidator;
    private CrawlerController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        crawlerService = mock(CrawlerService.class);
        stateService = mock(RedisCrawlStateService.class);
        urlValidator = new UrlValidator();
        controller = new CrawlerController(crawlerService, stateService, urlValidator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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
    @DisplayName("POST /crawler/start with JSON body containing omitted numeric fields uses defaults")
    void testJsonBodyOmittedNumericFields() throws Exception {
        when(stateService.isJobRunning(anyString())).thenReturn(false);
        when(stateService.getJobDetails(anyString())).thenReturn(
                CrawlJobDetails.builder().jobId("test-omitted").status(CrawlStatus.RUNNING).build()
        );

        String json = """
            {
                "seedUrl": "http://localhost:9000/page1.html",
                "jobId": "test-omitted"
            }
            """;

        mockMvc.perform(post("/crawler/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isAccepted());

        ArgumentCaptor<CrawlRequest> captor = ArgumentCaptor.forClass(CrawlRequest.class);
        verify(crawlerService, times(1)).crawl(captor.capture());
        CrawlRequest captured = captor.getValue();
        assertEquals(50, captured.getMaxPages());
        assertEquals(3, captured.getMaxDepth());
        assertEquals(4000, captured.getRequestTimeoutMs());
        assertEquals(50L, captured.getPolitenessDelayMs());
        assertTrue(captured.isStayOnDomain());
    }

    @Test
    @DisplayName("POST /crawler/start with explicitly null maxPages returns HTTP 400 validation error")
    void testJsonBodyExplicitNullMaxPages() throws Exception {
        String json = """
            {
                "seedUrl": "http://localhost:9000/page1.html",
                "maxPages": null
            }
            """;

        mockMvc.perform(post("/crawler/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("maxPages cannot be null"));

        verify(crawlerService, never()).crawl(any(CrawlRequest.class));
    }

    @Test
    @DisplayName("POST /crawler/start with explicitly null maxDepth returns HTTP 400 validation error")
    void testJsonBodyExplicitNullMaxDepth() throws Exception {
        String json = """
            {
                "seedUrl": "http://localhost:9000/page1.html",
                "maxDepth": null
            }
            """;

        mockMvc.perform(post("/crawler/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("maxDepth cannot be null"));

        verify(crawlerService, never()).crawl(any(CrawlRequest.class));
    }

    @Test
    @DisplayName("POST /crawler/start with invalid negative maxPages returns HTTP 400")
    void testJsonBodyInvalidNegativeMaxPages() throws Exception {
        String json = """
            {
                "seedUrl": "http://localhost:9000/page1.html",
                "maxPages": -5
            }
            """;

        mockMvc.perform(post("/crawler/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("maxPages must be greater than 0"));

        verify(crawlerService, never()).crawl(any(CrawlRequest.class));
    }

    @Test
    @DisplayName("POST /crawler/start with valid custom numeric fields passes correctly")
    void testJsonBodyValidCustomNumericFields() throws Exception {
        when(stateService.isJobRunning(anyString())).thenReturn(false);
        when(stateService.getJobDetails(anyString())).thenReturn(
                CrawlJobDetails.builder().jobId("test-custom").status(CrawlStatus.RUNNING).build()
        );

        String json = """
            {
                "seedUrl": "http://localhost:9000/page1.html",
                "jobId": "test-custom",
                "maxPages": 25,
                "maxDepth": 4,
                "requestTimeoutMs": 2500,
                "politenessDelayMs": 100
            }
            """;

        mockMvc.perform(post("/crawler/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isAccepted());

        ArgumentCaptor<CrawlRequest> captor = ArgumentCaptor.forClass(CrawlRequest.class);
        verify(crawlerService, times(1)).crawl(captor.capture());
        CrawlRequest captured = captor.getValue();
        assertEquals(25, captured.getMaxPages());
        assertEquals(4, captured.getMaxDepth());
        assertEquals(2500, captured.getRequestTimeoutMs());
        assertEquals(100L, captured.getPolitenessDelayMs());
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
