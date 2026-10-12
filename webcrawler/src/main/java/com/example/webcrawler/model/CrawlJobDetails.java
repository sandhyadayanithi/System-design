package com.example.webcrawler.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Detailed metrics, status, and configuration of an isolated crawl job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlJobDetails implements Serializable {
    private static final long serialVersionUID = 1L;

    private String jobId;
    private String seedUrl;
    private CrawlStatus status;
    private int maxPages;
    private int maxDepth;
    private String allowedDomain;

    private int pagesVisited;
    private int pagesDiscovered;
    private int pagesFailed;
    private int queueSize;

    private Long startTime;
    private Long endTime;
    private Long durationMs;
    private String message;
}
