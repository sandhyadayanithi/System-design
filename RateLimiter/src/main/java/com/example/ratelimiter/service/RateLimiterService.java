package com.example.ratelimiter.service;

import com.example.ratelimiter.model.RateLimitResult;
import com.example.ratelimiter.redis.RedisRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RateLimiterService {

    private final RedisRateLimiter redisRateLimiter;

    @Autowired
    public RateLimiterService(RedisRateLimiter redisRateLimiter) {
        this.redisRateLimiter = redisRateLimiter;
    }

    /**
     * Checks if the request is allowed for the specified client.
     * Consumes 1 token if allowed.
     * Logs the status with clientId and remaining tokens.
     *
     * @param clientId the client identifier
     * @return RateLimitResult indicating check results
     */
    public RateLimitResult checkRateLimit(String clientId) {
        RateLimitResult result = redisRateLimiter.isAllowed(clientId, 1);
        if (result.isAllowed()) {
            log.info("Client {} -> ALLOWED -> remaining tokens: {}", clientId, (int) result.getRemainingTokens());
        } else {
            log.warn("Client {} -> REJECTED -> remaining tokens: {} (wait time: {}s)", clientId, (int) result.getRemainingTokens(), result.getWaitTimeSeconds());
        }
        return result;
    }
}
