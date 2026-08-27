package com.example.autocomplete.controller.dto;

import com.example.autocomplete.model.SearchTerm;

import java.util.List;

/**
 * API response body. `source` and `tookMillis` are what make the caching
 * behaviour visible when testing with curl / Postman / JMeter.
 */
public record AutocompleteResponse(
        String prefix,
        int count,
        String source,
        long tookMillis,
        List<SearchTerm> suggestions
) {
}
