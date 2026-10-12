package com.example.webcrawler.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class CrawlRequestValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Omitted numeric fields receive sensible defaults")
    void testOmittedNumericFieldsReceiveDefaults() throws Exception {
        String json = """
            {
                "seedUrl": "http://localhost:9000/page1.html",
                "jobId": "job-omitted-test"
            }
            """;

        CrawlRequest request = objectMapper.readValue(json, CrawlRequest.class);

        assertNotNull(request);
        assertEquals("http://localhost:9000/page1.html", request.getSeedUrl());
        assertEquals("job-omitted-test", request.getJobId());
        assertEquals(50, request.getMaxPages());
        assertEquals(3, request.getMaxDepth());
        assertEquals(4000, request.getRequestTimeoutMs());
        assertEquals(50L, request.getPolitenessDelayMs());
        assertTrue(request.isStayOnDomain());
        assertNull(request.validate());
    }

    @Test
    @DisplayName("Explicitly supplied null maxPages fails validation")
    void testExplicitNullMaxPages() throws Exception {
        String json = """
            {
                "seedUrl": "http://localhost:9000/page1.html",
                "maxPages": null
            }
            """;

        CrawlRequest request = objectMapper.readValue(json, CrawlRequest.class);
        assertNotNull(request);
        assertEquals("maxPages cannot be null", request.validate());
    }

    @Test
    @DisplayName("Explicitly supplied null maxDepth fails validation")
    void testExplicitNullMaxDepth() throws Exception {
        String json = """
            {
                "seedUrl": "http://localhost:9000/page1.html",
                "maxDepth": null
            }
            """;

        CrawlRequest request = objectMapper.readValue(json, CrawlRequest.class);
        assertNotNull(request);
        assertEquals("maxDepth cannot be null", request.validate());
    }

    @Test
    @DisplayName("Explicitly supplied null requestTimeoutMs fails validation")
    void testExplicitNullTimeout() throws Exception {
        String json = """
            {
                "seedUrl": "http://localhost:9000/page1.html",
                "requestTimeoutMs": null
            }
            """;

        CrawlRequest request = objectMapper.readValue(json, CrawlRequest.class);
        assertNotNull(request);
        assertEquals("requestTimeoutMs cannot be null", request.validate());
    }

    @Test
    @DisplayName("Explicitly supplied null politenessDelayMs fails validation")
    void testExplicitNullPolitenessDelay() throws Exception {
        String json = """
            {
                "seedUrl": "http://localhost:9000/page1.html",
                "politenessDelayMs": null
            }
            """;

        CrawlRequest request = objectMapper.readValue(json, CrawlRequest.class);
        assertNotNull(request);
        assertEquals("politenessDelayMs cannot be null", request.validate());
    }

    @Test
    @DisplayName("Invalid zero or negative numeric values fail validation")
    void testInvalidNumericValues() throws Exception {
        // maxPages <= 0
        String jsonZeroPages = "{\"seedUrl\": \"http://localhost:9000\", \"maxPages\": 0}";
        assertEquals("maxPages must be greater than 0", objectMapper.readValue(jsonZeroPages, CrawlRequest.class).validate());

        String jsonNegativePages = "{\"seedUrl\": \"http://localhost:9000\", \"maxPages\": -5}";
        assertEquals("maxPages must be greater than 0", objectMapper.readValue(jsonNegativePages, CrawlRequest.class).validate());

        // maxDepth < 0
        String jsonNegativeDepth = "{\"seedUrl\": \"http://localhost:9000\", \"maxDepth\": -1}";
        assertEquals("maxDepth must be greater than or equal to 0", objectMapper.readValue(jsonNegativeDepth, CrawlRequest.class).validate());

        // requestTimeoutMs <= 0
        String jsonZeroTimeout = "{\"seedUrl\": \"http://localhost:9000\", \"requestTimeoutMs\": 0}";
        assertEquals("requestTimeoutMs must be greater than 0", objectMapper.readValue(jsonZeroTimeout, CrawlRequest.class).validate());

        // politenessDelayMs < 0
        String jsonNegativeDelay = "{\"seedUrl\": \"http://localhost:9000\", \"politenessDelayMs\": -10}";
        assertEquals("politenessDelayMs must be greater than or equal to 0", objectMapper.readValue(jsonNegativeDelay, CrawlRequest.class).validate());
    }

    @Test
    @DisplayName("Valid custom numeric values are accepted without error")
    void testValidCustomNumericValues() throws Exception {
        String json = """
            {
                "seedUrl": "http://localhost:9000/page1.html",
                "maxPages": 25,
                "maxDepth": 4,
                "requestTimeoutMs": 3500,
                "politenessDelayMs": 100,
                "stayOnDomain": false
            }
            """;

        CrawlRequest request = objectMapper.readValue(json, CrawlRequest.class);
        assertNotNull(request);
        assertEquals(25, request.getMaxPages());
        assertEquals(4, request.getMaxDepth());
        assertEquals(3500, request.getRequestTimeoutMs());
        assertEquals(100L, request.getPolitenessDelayMs());
        assertFalse(request.isStayOnDomain());
        assertNull(request.validate());
    }
}
