package com.example.autocomplete.service;

import com.example.autocomplete.model.SearchTerm;
import com.example.autocomplete.trie.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache-aside autocomplete lookup.
 *
 *   1. look for autocomplete:<prefix>:<limit> in Redis
 *   2. HIT  -> return the cached suggestions, no trie work at all
 *      MISS -> run the trie prefix search, then write the result back to Redis with a TTL
 *
 * If Redis is unreachable the request is still served from the trie; the cache is
 * an optimisation, not a dependency.
 */
@Service
public class AutocompleteService {

    private static final Logger log = LoggerFactory.getLogger(AutocompleteService.class);
    private static final String KEY_PREFIX = "autocomplete:";

    /** Which layer answered a particular request. */
    public enum Source {
        CACHE, TRIE
    }

    /** Suggestions plus where they came from, so the API can report the cache behaviour. */
    public record Result(List<SearchTerm> suggestions, Source source) {
    }

    private final Trie trie;
    private final RedisTemplate<String, List<SearchTerm>> redisTemplate;
    private final Duration ttl;

    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong cacheErrors = new AtomicLong();

    public AutocompleteService(Trie trie,
                               RedisTemplate<String, List<SearchTerm>> redisTemplate,
                               @Value("${autocomplete.cache.ttl-seconds:300}") long ttlSeconds) {
        this.trie = trie;
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Result suggest(String prefix, int limit) {
        String normalized = prefix == null ? "" : prefix.trim().toLowerCase();
        String key = KEY_PREFIX + normalized + ":" + limit;

        // 1. cache lookup
        try {
            List<SearchTerm> cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                cacheHits.incrementAndGet();
                log.debug("Cache HIT for {}", key);
                return new Result(cached, Source.CACHE);
            }
        } catch (Exception e) {
            // Redis down / unreachable -> degrade to trie-only instead of failing
            cacheErrors.incrementAndGet();
            log.warn("Redis unavailable on read for key {} ({}), falling back to Trie", key, e.getMessage());
            return new Result(trie.searchByPrefix(normalized, limit), Source.TRIE);
        }

        // 2. cache miss -> trie prefix search
        cacheMisses.incrementAndGet();
        List<SearchTerm> suggestions = trie.searchByPrefix(normalized, limit);

        // 3. populate the cache for subsequent requests
        try {
            redisTemplate.opsForValue().set(key, suggestions, ttl);
        } catch (Exception e) {
            cacheErrors.incrementAndGet();
            log.warn("Redis unavailable on write for key {} ({})", key, e.getMessage());
        }

        return new Result(suggestions, Source.TRIE);
    }

    public int trieSize() {
        return trie.size();
    }

    public long getCacheHits() {
        return cacheHits.get();
    }

    public long getCacheMisses() {
        return cacheMisses.get();
    }

    public long getCacheErrors() {
        return cacheErrors.get();
    }

    public long getCacheTtlSeconds() {
        return ttl.getSeconds();
    }
}
