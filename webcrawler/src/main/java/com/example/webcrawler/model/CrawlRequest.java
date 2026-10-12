package com.example.webcrawler.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload to configure and initiate an asynchronous crawl job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlRequest {

    /** The starting URL for crawling (Required) */
    private String seedUrl;

    /** Unique identifier for the crawl job (Optional, auto-generated if omitted) */
    private String jobId;

    /** Maximum total pages to crawl (Default: 50) */
    @Builder.Default
    private int maxPages = 50;

    /** Maximum crawl distance from seed URL (Default: 3) */
    @Builder.Default
    private int maxDepth = 3;

    /** Permitted host/domain. If set, URLs outside this domain are ignored */
    private String allowedDomain;

    /** If true and allowedDomain is null, automatically lock crawl to seed URL's host (Default: true) */
    @Builder.Default
    private boolean stayOnDomain = true;

    /** HTTP request timeout in milliseconds (Default: 4000) */
    @Builder.Default
    private int requestTimeoutMs = 4000;

    /** Politeness delay between consecutive HTTP requests in milliseconds (Default: 50) */
    @Builder.Default
    private long politenessDelayMs = 50;
}
