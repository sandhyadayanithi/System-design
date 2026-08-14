package com.example.ratelimiter;

import com.example.ratelimiter.model.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RateLimiterIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private RestClient restClient;

    @BeforeEach
    public void setup() {
        // Build a RestClient pointing at the locally running server
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        // Clear all rate limit keys before each test for full isolation
        Set<String> keys = redisTemplate.keys("rate_limit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ─── Helper ─────────────────────────────────────────────────────────────────

    /**
     * Sends GET /api/test with optional X-Client-Id header.
     * Returns the ResponseEntity regardless of HTTP status (no exception on 4xx/5xx).
     */
    private ResponseEntity<ApiResponse> sendRequest(String clientId) {
        RestClient.RequestHeadersSpec<?> spec = restClient.get().uri("/api/test");
        if (clientId != null) {
            spec = spec.header("X-Client-Id", clientId);
        }
        return spec.retrieve()
                .onStatus(status -> true, (req, res) -> { /* no-op — handle all statuses ourselves */ })
                .toEntity(ApiResponse.class);
    }

    // ─── Test 1: First 10 requests allowed, 11th rejected ───────────────────────

    @Test
    public void testInitialRequestsAllowedAndBurstRejected() {
        String clientId = "client-initial-" + UUID.randomUUID();

        // First 10 requests must all succeed (bucket starts full at capacity=10)
        for (int i = 0; i < 10; i++) {
            ResponseEntity<ApiResponse> response = sendRequest(clientId);
            assertEquals(HttpStatus.OK, response.getStatusCode(),
                    "Request " + (i + 1) + " should be allowed");
            assertNotNull(response.getBody());
            assertTrue(response.getBody().getAllowed(),
                    "Request " + (i + 1) + " allowed field should be true");
            assertEquals(9 - i, response.getBody().getRemainingTokens(),
                    "Remaining tokens should be " + (9 - i) + " after request " + (i + 1));
        }

        // The 11th request must be rejected with HTTP 429
        ResponseEntity<ApiResponse> rejected = sendRequest(clientId);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, rejected.getStatusCode(),
                "11th request should be rate limited (HTTP 429)");
        assertNotNull(rejected.getBody());
        assertFalse(rejected.getBody().getAllowed(), "allowed should be false");
        assertEquals(0, rejected.getBody().getRemainingTokens(), "remaining tokens should be 0");
        assertEquals("Rate limit exceeded", rejected.getBody().getMessage());
        assertNotNull(rejected.getHeaders().getFirst("Retry-After"),
                "Retry-After header must be present on 429 response");
    }

    // ─── Test 2: Token refill over elapsed time ──────────────────────────────────

    @Test
    public void testTokenRefillOverTime() throws InterruptedException {
        String clientId = "client-refill-" + UUID.randomUUID();

        // Drain the full bucket (10 tokens)
        for (int i = 0; i < 10; i++) {
            sendRequest(clientId);
        }

        // Confirm bucket is empty
        ResponseEntity<ApiResponse> rejected = sendRequest(clientId);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, rejected.getStatusCode(),
                "Bucket should be exhausted after 10 requests");

        // Wait 1.1 seconds — refill-rate=2 tokens/sec → ~2 new tokens added
        Thread.sleep(1100);

        // At least one request must now be allowed
        ResponseEntity<ApiResponse> allowed = sendRequest(clientId);
        assertEquals(HttpStatus.OK, allowed.getStatusCode(),
                "After 1.1s wait, at least 2 tokens should have been refilled");
        assertNotNull(allowed.getBody());
        assertTrue(allowed.getBody().getAllowed(), "Request should be allowed after refill");

        int remaining = allowed.getBody().getRemainingTokens();
        assertTrue(remaining >= 0 && remaining <= 10,
                "Remaining tokens must be between 0 and capacity (10), got: " + remaining);
    }

    // ─── Test 3: Different clients have completely independent buckets ────────────

    @Test
    public void testDifferentClientsHaveIndependentBuckets() {
        String clientA = "clientA-" + UUID.randomUUID();
        String clientB = "clientB-" + UUID.randomUUID();

        // Drain clientA's bucket completely (10 tokens)
        for (int i = 0; i < 10; i++) {
            assertEquals(HttpStatus.OK, sendRequest(clientA).getStatusCode(),
                    "clientA request " + (i + 1) + " should be allowed");
        }

        // clientA must now be blocked
        ResponseEntity<ApiResponse> clientARejected = sendRequest(clientA);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, clientARejected.getStatusCode(),
                "clientA should be rate limited after 10 requests");

        // clientB must still be fully available — its bucket is independent
        ResponseEntity<ApiResponse> clientBAllowed = sendRequest(clientB);
        assertEquals(HttpStatus.OK, clientBAllowed.getStatusCode(),
                "clientB should NOT be affected by clientA's rate limit");
        assertNotNull(clientBAllowed.getBody());
        assertTrue(clientBAllowed.getBody().getAllowed());
        assertEquals(9, clientBAllowed.getBody().getRemainingTokens(),
                "clientB should have 9 tokens remaining after its first request");
    }

    // ─── Test 4: Missing X-Client-Id header → HTTP 400 ──────────────────────────

    @Test
    public void testMissingClientIdHeaderReturnsBadRequest() {
        ResponseEntity<ApiResponse> response = sendRequest(null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "Missing X-Client-Id should return HTTP 400");
        assertNotNull(response.getBody());
        assertEquals("Missing client identifier header 'X-Client-Id'",
                response.getBody().getMessage());
    }

    // ─── Test 5: Concurrent requests — Redis Lua guarantees atomicity ────────────

    @Test
    public void testConcurrentRequestsAtomicityViaLua() throws InterruptedException, ExecutionException {
        String clientId = "client-concurrent-" + UUID.randomUUID();
        int totalRequests = 20; // 10 should succeed (capacity), 10 should be rejected

        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<HttpStatusCode>> futures = new ArrayList<>();

        for (int i = 0; i < totalRequests; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await(); // All threads release simultaneously
                ResponseEntity<ApiResponse> response = sendRequest(clientId);
                return response.getStatusCode();
            }));
        }

        startLatch.countDown(); // Fire all threads at once
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS),
                "All concurrent requests must complete within 15 seconds");

        long okCount = 0;
        long tooManyCount = 0;
        for (Future<HttpStatusCode> f : futures) {
            HttpStatusCode status = f.get();
            if (status.value() == 200) okCount++;
            else if (status.value() == 429) tooManyCount++;
        }

        System.out.printf("Concurrency test → allowed: %d, rejected: %d%n", okCount, tooManyCount);

        // The Lua script guarantees: exactly capacity=10 requests succeed
        assertEquals(10, okCount,
                "Exactly 10 concurrent requests must be allowed (Redis Lua atomic guarantee)");
        assertEquals(10, tooManyCount,
                "Exactly 10 concurrent requests must be rejected");
    }
}
