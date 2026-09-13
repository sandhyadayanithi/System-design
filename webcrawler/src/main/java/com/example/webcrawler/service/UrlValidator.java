package com.example.webcrawler.service;

import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class UrlValidator {

    public boolean isValid(String url) {

        try {
            URI uri = URI.create(url);

            String scheme = uri.getScheme();

            return ("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null;

        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}