package com.example.webcrawler.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * Request payload to configure and initiate an asynchronous crawl job.
 */
@Data
@Builder
@AllArgsConstructor
public class CrawlRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_MAX_PAGES = 50;
    public static final int DEFAULT_MAX_DEPTH = 3;
    public static final int DEFAULT_REQUEST_TIMEOUT_MS = 4000;
    public static final long DEFAULT_POLITENESS_DELAY_MS = 50L;
    public static final boolean DEFAULT_STAY_ON_DOMAIN = true;

    /** The starting URL for crawling (Required) */
    private String seedUrl;

    /** Unique identifier for the crawl job (Optional, auto-generated if omitted) */
    private String jobId;

    /** Maximum total pages to crawl (Default: 50) */
    @Builder.Default
    private Integer maxPages = DEFAULT_MAX_PAGES;

    /** Maximum crawl distance from seed URL (Default: 3) */
    @Builder.Default
    private Integer maxDepth = DEFAULT_MAX_DEPTH;

    /** Permitted host/domain. If set, URLs outside this domain are ignored */
    private String allowedDomain;

    /** If true and allowedDomain is null, automatically lock crawl to seed URL's host (Default: true) */
    @Builder.Default
    private Boolean stayOnDomain = DEFAULT_STAY_ON_DOMAIN;

    /** HTTP request timeout in milliseconds (Default: 4000) */
    @Builder.Default
    private Integer requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS;

    /** Politeness delay between consecutive HTTP requests in milliseconds (Default: 50) */
    @Builder.Default
    private Long politenessDelayMs = DEFAULT_POLITENESS_DELAY_MS;

    // Internal flags to detect explicitly supplied null values in JSON
    @JsonIgnore
    @Builder.Default
    private boolean maxPagesExplicitlyNull = false;

    @JsonIgnore
    @Builder.Default
    private boolean maxDepthExplicitlyNull = false;

    @JsonIgnore
    @Builder.Default
    private boolean requestTimeoutMsExplicitlyNull = false;

    @JsonIgnore
    @Builder.Default
    private boolean politenessDelayMsExplicitlyNull = false;

    @JsonIgnore
    @Builder.Default
    private boolean stayOnDomainExplicitlyNull = false;

    @JsonCreator
    public CrawlRequest() {
        this.maxPages = DEFAULT_MAX_PAGES;
        this.maxDepth = DEFAULT_MAX_DEPTH;
        this.stayOnDomain = DEFAULT_STAY_ON_DOMAIN;
        this.requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS;
        this.politenessDelayMs = DEFAULT_POLITENESS_DELAY_MS;
    }

    @JsonProperty("maxPages")
    public void setMaxPages(Integer maxPages) {
        if (maxPages == null) {
            this.maxPagesExplicitlyNull = true;
            this.maxPages = null;
        } else {
            this.maxPages = maxPages;
        }
    }

    @JsonProperty("maxDepth")
    public void setMaxDepth(Integer maxDepth) {
        if (maxDepth == null) {
            this.maxDepthExplicitlyNull = true;
            this.maxDepth = null;
        } else {
            this.maxDepth = maxDepth;
        }
    }

    @JsonProperty("requestTimeoutMs")
    public void setRequestTimeoutMs(Integer requestTimeoutMs) {
        if (requestTimeoutMs == null) {
            this.requestTimeoutMsExplicitlyNull = true;
            this.requestTimeoutMs = null;
        } else {
            this.requestTimeoutMs = requestTimeoutMs;
        }
    }

    @JsonProperty("politenessDelayMs")
    public void setPolitenessDelayMs(Long politenessDelayMs) {
        if (politenessDelayMs == null) {
            this.politenessDelayMsExplicitlyNull = true;
            this.politenessDelayMs = null;
        } else {
            this.politenessDelayMs = politenessDelayMs;
        }
    }

    @JsonProperty("stayOnDomain")
    public void setStayOnDomain(Boolean stayOnDomain) {
        if (stayOnDomain == null) {
            this.stayOnDomainExplicitlyNull = true;
            this.stayOnDomain = null;
        } else {
            this.stayOnDomain = stayOnDomain;
        }
    }

    /**
     * Backward-compatibility helper for boolean flag.
     */
    public boolean isStayOnDomain() {
        return stayOnDomain != null ? stayOnDomain : DEFAULT_STAY_ON_DOMAIN;
    }

    /**
     * Validates numeric constraints and explicit null states.
     * Returns an error message if invalid, or null if valid.
     */
    public String validate() {
        if (maxPagesExplicitlyNull) {
            return "maxPages cannot be null";
        }
        if (maxPages != null && maxPages <= 0) {
            return "maxPages must be greater than 0";
        }

        if (maxDepthExplicitlyNull) {
            return "maxDepth cannot be null";
        }
        if (maxDepth != null && maxDepth < 0) {
            return "maxDepth must be greater than or equal to 0";
        }

        if (requestTimeoutMsExplicitlyNull) {
            return "requestTimeoutMs cannot be null";
        }
        if (requestTimeoutMs != null && requestTimeoutMs <= 0) {
            return "requestTimeoutMs must be greater than 0";
        }

        if (politenessDelayMsExplicitlyNull) {
            return "politenessDelayMs cannot be null";
        }
        if (politenessDelayMs != null && politenessDelayMs < 0) {
            return "politenessDelayMs must be greater than or equal to 0";
        }

        return null;
    }
}
