package com.example.ratelimiter.redis;

import com.example.ratelimiter.config.RateLimitProperties;
import com.example.ratelimiter.model.RateLimitResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class RedisRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> rateLimiterScript;
    private final RateLimitProperties properties;

    @Autowired
    public RedisRateLimiter(StringRedisTemplate redisTemplate,
                            RedisScript<List> rateLimiterScript,
                            RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.rateLimiterScript = rateLimiterScript;
        this.properties = properties;
    }

    /**
     * Checks if a client is allowed to consume the requested number of tokens.
     * Runs the Redis Lua script atomically.
     *
     * @param clientId         Unique client identifier
     * @param tokensRequested  Number of tokens to consume
     * @return RateLimitResult indicating allowed/rejected status, remaining tokens, and wait time.
     */
    public RateLimitResult isAllowed(String clientId, int tokensRequested) {
        String key = "rate_limit:" + clientId;
        String capacityStr = String.valueOf(properties.getCapacity());
        String refillRateStr = String.valueOf(properties.getRefillRate());
        String requestedStr = String.valueOf(tokensRequested);
        String nowStr = String.valueOf(System.currentTimeMillis());

        try {
            // Execute script
            List<String> result = redisTemplate.execute(
                    rateLimiterScript,
                    Collections.singletonList(key),
                    capacityStr, refillRateStr, requestedStr, nowStr
            );

            if (result == null || result.size() < 3) {
                // Fallback: fail-open if Redis execution results are invalid
                return new RateLimitResult(true, properties.getCapacity(), 0.0);
            }

            boolean allowed = "1".equals(result.get(0));
            double remainingTokens = Double.parseDouble(result.get(1));
            double waitTimeSeconds = Double.parseDouble(result.get(2));

            return new RateLimitResult(allowed, remainingTokens, waitTimeSeconds);
        } catch (Exception e) {
            // Fail-open: print error and allow the traffic in case of Redis connectivity issues
            System.err.println("Redis Rate Limiter failed, failing open: " + e.getMessage());
            return new RateLimitResult(true, properties.getCapacity(), 0.0);
        }
    }
}
