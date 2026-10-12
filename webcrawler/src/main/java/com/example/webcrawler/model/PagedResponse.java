package com.example.webcrawler.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Generic response wrapper for paginated collections of discovered/visited/queued URLs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {

    private List<T> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;

    public static <T> PagedResponse<T> of(List<T> allItems, int page, int size) {
        if (allItems == null || allItems.isEmpty()) {
            return new PagedResponse<>(Collections.emptyList(), page, size, 0, 0);
        }

        int total = allItems.size();
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? 50 : size;

        int fromIndex = safePage * safeSize;
        if (fromIndex >= total) {
            return new PagedResponse<>(Collections.emptyList(), safePage, safeSize, total, (int) Math.ceil((double) total / safeSize));
        }

        int toIndex = Math.min(fromIndex + safeSize, total);
        List<T> subList = allItems.subList(fromIndex, toIndex);
        int totalPages = (int) Math.ceil((double) total / safeSize);

        return new PagedResponse<>(subList, safePage, safeSize, total, totalPages);
    }
}
