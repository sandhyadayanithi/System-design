package com.example.urlshortener.controller;


import com.example.urlshortener.dto.urlRequest;
import com.example.urlshortener.dto.urlResponse;
import com.example.urlshortener.service.urlService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
public class urlController {
    private final urlService service;


    public urlController(urlService service){
        this.service = service;
    }

    @PostMapping("/shorten")
    public urlResponse shortenUrl(
            @RequestBody urlRequest request) {

        String shortUrl =
                service.createShortUrl(request.getLongUrl());

        return new urlResponse("http://localhost:8081/" + shortUrl);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode) {

        String longUrl = service.getLongUrl(shortCode);

        if (longUrl == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(302)
                .location(URI.create(longUrl))
                .build();
    }
}

