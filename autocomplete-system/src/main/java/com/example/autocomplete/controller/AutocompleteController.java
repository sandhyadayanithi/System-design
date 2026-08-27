package com.example.autocomplete.controller;

import com.example.autocomplete.controller.dto.AutocompleteResponse;
import com.example.autocomplete.service.AutocompleteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AutocompleteController {

    private static final int MAX_LIMIT = 50;

    private final AutocompleteService service;

    public AutocompleteController(AutocompleteService service) {
        this.service = service;
    }

    /**
     * GET /api/autocomplete?prefix=app&limit=10
     */
    @GetMapping("/autocomplete")
    public AutocompleteResponse autocomplete(@RequestParam("prefix") String prefix,
                                             @RequestParam(name = "limit", defaultValue = "10") int limit) {
        if (prefix == null || prefix.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prefix must not be empty");
        }
        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be greater than 0");
        }
        int effectiveLimit = Math.min(limit, MAX_LIMIT);

        long start = System.nanoTime();
        AutocompleteService.Result result = service.suggest(prefix, effectiveLimit);
        long tookMillis = (System.nanoTime() - start) / 1_000_000;

        return new AutocompleteResponse(
                prefix.trim().toLowerCase(),
                result.suggestions().size(),
                result.source().name(),
                tookMillis,
                result.suggestions());
    }

    /**
     * GET /api/health — trie size and cache counters.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("trieSize", service.trieSize());
        body.put("cacheHits", service.getCacheHits());
        body.put("cacheMisses", service.getCacheMisses());
        body.put("cacheErrors", service.getCacheErrors());
        body.put("cacheTtlSeconds", service.getCacheTtlSeconds());
        return ResponseEntity.ok(body);
    }
}
