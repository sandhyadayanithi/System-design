package com.example.ratelimiter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RateLimitResult {
    private boolean allowed;
    private double remainingTokens;
    private double waitTimeSeconds;
}
