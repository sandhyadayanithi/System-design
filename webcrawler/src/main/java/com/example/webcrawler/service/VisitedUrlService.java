package com.example.webcrawler.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class VisitedUrlService {

    private static final String VISITED_KEY = "crawler:visited";

    private final RedisTemplate<String, String> redisTemplate;

    public VisitedUrlService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isVisited(String url) {

        Boolean result = redisTemplate.opsForSet()
                .isMember(VISITED_KEY, url);

        return Boolean.TRUE.equals(result);
    }

    public void markVisited(String url) {

        redisTemplate.opsForSet()
                .add(VISITED_KEY, url);
    }

    public Set<String> getVisitedUrls() {

        Set<String> urls = redisTemplate.opsForSet()
                .members(VISITED_KEY);

        return urls;
    }

    public void clearVisitedUrls() {

        redisTemplate.delete(VISITED_KEY);
    }
}