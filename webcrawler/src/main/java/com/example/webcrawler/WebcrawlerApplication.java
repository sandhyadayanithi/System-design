package com.example.webcrawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class WebcrawlerApplication {

    public static void main(String[] args) {

        SpringApplication.run(
                WebcrawlerApplication.class,
                args
        );
    }
}