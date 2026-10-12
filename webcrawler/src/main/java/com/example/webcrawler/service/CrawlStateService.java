package com.example.webcrawler.service;

import com.example.webcrawler.model.CrawlJobDetails;
import com.example.webcrawler.model.CrawlStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Backward-compatible service wrapper delegating to RedisCrawlStateService.
 */
@Service
public class CrawlStateService {

    private final RedisCrawlStateService stateService;

    public CrawlStateService(RedisCrawlStateService stateService) {
        this.stateService = stateService;
    }

    public void markDiscovered(String url) {
        stateService.markDiscoveredAtomic(stateService.getLatestJobId(), url);
    }

    public Set<String> getDiscoveredUrls() {
        return new HashSet<>(stateService.getDiscoveredUrls(stateService.getLatestJobId()));
    }

    public void setStatus(CrawlStatus status) {
        stateService.setStatus(stateService.getLatestJobId(), status, null);
    }

    public CrawlStatus getStatus() {
        CrawlJobDetails details = stateService.getJobDetails(stateService.getLatestJobId());
        return details != null ? details.getStatus() : CrawlStatus.IDLE;
    }

    public void clearState() {
        stateService.clearJobState(stateService.getLatestJobId());
    }
}