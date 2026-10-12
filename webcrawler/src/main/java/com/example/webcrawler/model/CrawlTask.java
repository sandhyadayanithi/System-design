package com.example.webcrawler.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Represents a URL waiting in the frontier queue with depth and provenance metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlTask implements Serializable {
    private static final long serialVersionUID = 1L;

    private String url;
    private int depth;
    private String parentUrl;
    private long discoveredTime;

    public CrawlTask(String url, int depth, String parentUrl) {
        this.url = url;
        this.depth = depth;
        this.parentUrl = parentUrl;
        this.discoveredTime = System.currentTimeMillis();
    }
}
