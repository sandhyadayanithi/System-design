package com.example.webcrawler.service;

import com.example.webcrawler.model.CrawlStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class CrawlStateService {

    private static final String DISCOVERED_KEY = "crawler:discovered";
    private static final String STATUS_KEY = "crawler:status";

    private final RedisTemplate<String, String> redisTemplate;

    public CrawlStateService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void markDiscovered(String url) {
        redisTemplate.opsForSet().add(DISCOVERED_KEY, url);
    }

    public Set<String> getDiscoveredUrls() {
        return redisTemplate.opsForSet().members(DISCOVERED_KEY);
    }

    public void setStatus(CrawlStatus status) {
        redisTemplate.opsForValue()
                .set(STATUS_KEY, status.name());
    }

    public CrawlStatus getStatus() {

        String status = redisTemplate.opsForValue()
                .get(STATUS_KEY);

        if (status == null) {
            return CrawlStatus.IDLE;
        }

        return CrawlStatus.valueOf(status);
    }

    public void clearState() {
        redisTemplate.delete(DISCOVERED_KEY);
        redisTemplate.delete(STATUS_KEY);
    }
}