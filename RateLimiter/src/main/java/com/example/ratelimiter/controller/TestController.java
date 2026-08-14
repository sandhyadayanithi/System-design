package com.example.ratelimiter.controller;

import com.example.ratelimiter.exception.MissingClientIdException;
import com.example.ratelimiter.model.ApiResponse;
import com.example.ratelimiter.model.RateLimitResult;
import com.example.ratelimiter.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {

    private final RateLimiterService rateLimiterService;

    @Autowired
    public TestController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Test endpoint to demonstrate and verify rate limiting behavior.
     * Requires the HTTP Header "X-Client-Id".
     */
    @GetMapping("/test")
    public ResponseEntity<ApiResponse> testRateLimit(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId) {

        if (clientId == null || clientId.trim().isEmpty()) {
            throw new MissingClientIdException("Missing client identifier header 'X-Client-Id'");
        }

        RateLimitResult result = rateLimiterService.checkRateLimit(clientId);

        if (result.isAllowed()) {
            ApiResponse response = ApiResponse.builder()
                    .allowed(true)
                    .clientId(clientId)
                    .remainingTokens((int) result.getRemainingTokens())
                    .message("Request allowed")
                    .build();
            return ResponseEntity.ok(response);
        } else {
            ApiResponse response = ApiResponse.builder()
                    .allowed(false)
                    .clientId(clientId)
                    .remainingTokens((int) result.getRemainingTokens())
                    .message("Rate limit exceeded")
                    .build();

            // HTTP spec requires Retry-After in seconds as integer.
            int retryAfter = (int) Math.ceil(result.getWaitTimeSeconds());

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(retryAfter))
                    .body(response);
        }
    }
}
