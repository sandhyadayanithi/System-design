package com.example.webcrawler.service;

import com.example.webcrawler.model.CrawlStatus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CrawlerService {

    private final UrlQueue urlQueue;
    private final VisitedUrlService visitedUrlService;
    private final UrlValidator urlValidator;
    private final CrawlStateService crawlStateService;

    public CrawlerService(
            UrlQueue urlQueue,
            VisitedUrlService visitedUrlService,
            UrlValidator urlValidator,
            CrawlStateService crawlStateService) {

        this.urlQueue = urlQueue;
        this.visitedUrlService = visitedUrlService;
        this.urlValidator = urlValidator;
        this.crawlStateService = crawlStateService;
    }

    @Async
    public void crawl(String seedUrl) {

        if (crawlStateService.getStatus() == CrawlStatus.RUNNING) {
            return;
        }

        if (!urlValidator.isValid(seedUrl)) {
            crawlStateService.setStatus(CrawlStatus.FAILED);
            return;
        }

        crawlStateService.setStatus(CrawlStatus.RUNNING);

        urlQueue.clear();
        urlQueue.add(seedUrl);
        crawlStateService.markDiscovered(seedUrl);

        String url;

        try {

            while ((url = urlQueue.getNext()) != null) {

                if (!urlValidator.isValid(url)) {
                    continue;
                }

                if (visitedUrlService.isVisited(url)) {
                    continue;
                }

                visitedUrlService.markVisited(url);

                try {

                    System.out.println("Crawling: " + url);

                    Document document = Jsoup.connect(url)
                            .get();

                    Elements links = document.select("a[href]");

                    for (Element link : links) {

                        String discoveredUrl =
                                link.absUrl("href");

                        if (urlValidator.isValid(discoveredUrl)) {

                            crawlStateService
                                    .markDiscovered(discoveredUrl);

                            if (!visitedUrlService
                                    .isVisited(discoveredUrl)) {

                                urlQueue.add(discoveredUrl);
                            }
                        }
                    }

                } catch (Exception e) {

                    System.out.println(
                            "Failed to crawl: " + url
                    );
                }
            }

            crawlStateService
                    .setStatus(CrawlStatus.COMPLETED);

        } catch (Exception e) {

            crawlStateService
                    .setStatus(CrawlStatus.FAILED);
        }
    }

    public CrawlStatus getStatus() {
        return crawlStateService.getStatus();
    }
}