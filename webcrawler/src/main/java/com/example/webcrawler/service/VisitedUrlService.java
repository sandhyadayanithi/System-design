package com.example.webcrawler.service;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Backward-compatible service wrapper delegating to RedisCrawlStateService.
 */
@Service
public class VisitedUrlService {

    private final RedisCrawlStateService stateService;

    public VisitedUrlService(RedisCrawlStateService stateService) {
        this.stateService = stateService;
    }

    public boolean isVisited(String url) {
        return stateService.isVisited(stateService.getLatestJobId(), url);
    }

    public void markVisited(String url) {
        stateService.markVisitedAtomic(stateService.getLatestJobId(), url);
    }

    public Set<String> getVisitedUrls() {
        return new HashSet<>(stateService.getVisitedUrls(stateService.getLatestJobId()));
    }

    public void clearVisitedUrls() {
        stateService.clearJobState(stateService.getLatestJobId());
    }
}