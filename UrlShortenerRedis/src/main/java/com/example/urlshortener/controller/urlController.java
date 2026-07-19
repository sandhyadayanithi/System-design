package com.example.urlshortener.controller;


import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.urlshortener.dto.urlRequest;
import com.example.urlshortener.dto.urlResponse;
import com.example.urlshortener.service.urlService;

@RestController
public class urlController {
    private final urlService service;

    public urlController(urlService service){
        this.service = service;
    }

    @PostMapping("/shorten")
    public urlResponse shortenUrl(
            @RequestBody urlRequest request) {
        System.out.println("POST request received");
        String shortUrl =
                service.createShortUrl(request.getLongUrl());

        return new urlResponse("http://localhost:8081/" + shortUrl);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable("shortCode") String shortUrl) {

        String longUrl = service.getLongUrl(shortUrl);

        if(longUrl == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(302).location(URI.create(longUrl)).build();
    }
}

