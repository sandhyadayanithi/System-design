package com.example.webcrawler.service;

import com.example.webcrawler.model.CrawlTask;
import org.springframework.stereotype.Component;

/**
 * Backward-compatible URL queue wrapper delegating to RedisCrawlStateService.
 */
@Component
public class UrlQueue {

    private final RedisCrawlStateService stateService;

    public UrlQueue(RedisCrawlStateService stateService) {
        this.stateService = stateService;
    }

    public void add(String url) {
        stateService.enqueue(stateService.getLatestJobId(), new CrawlTask(url, 0, null));
    }

    public String getNext() {
        CrawlTask task = stateService.dequeue(stateService.getLatestJobId());
        return task != null ? task.getUrl() : null;
    }

    public int size() {
        return stateService.getQueueSize(stateService.getLatestJobId());
    }

    public void clear() {
        // Handled via clearJobState
    }
}