package com.example.webcrawler.controller;

import com.example.webcrawler.model.CrawlStatus;
import com.example.webcrawler.service.CrawlStateService;
import com.example.webcrawler.service.CrawlerService;
import com.example.webcrawler.service.VisitedUrlService;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/crawler")
public class CrawlerController {

    private final CrawlerService crawlerService;
    private final VisitedUrlService visitedUrlService;
    private final CrawlStateService crawlStateService;

    public CrawlerController(
            CrawlerService crawlerService,
            VisitedUrlService visitedUrlService,
            CrawlStateService crawlStateService) {

        this.crawlerService = crawlerService;
        this.visitedUrlService = visitedUrlService;
        this.crawlStateService = crawlStateService;
    }

    @PostMapping("/start")
    public String startCrawling(
            @RequestParam String seedUrl) {

        crawlerService.crawl(seedUrl);

        return "Crawling started";
    }

    @GetMapping("/status")
    public CrawlStatus getStatus() {

        return crawlerService.getStatus();
    }

    @GetMapping("/visited")
    public Set<String> getVisitedUrls() {

        return visitedUrlService.getVisitedUrls();
    }

    @GetMapping("/discovered")
    public Set<String> getDiscoveredUrls() {

        return crawlStateService.getDiscoveredUrls();
    }

    @DeleteMapping("/visited")
    public String clearVisitedUrls() {

        visitedUrlService.clearVisitedUrls();

        return "Visited URL set cleared";
    }

    @DeleteMapping("/state")
    public String clearCrawlerState() {

        visitedUrlService.clearVisitedUrls();
        crawlStateService.clearState();

        return "Crawler state cleared";
    }
}