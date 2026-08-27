package com.example.autocomplete.trie;

import com.example.autocomplete.model.SearchTerm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrieTest {

    private Trie trie;

    @BeforeEach
    void setUp() {
        trie = new Trie();
        trie.insert("apple", 9500);
        trie.insert("app store", 8900);
        trie.insert("application", 7200);
        trie.insert("appointment", 6100);
        trie.insert("banana", 4000);
    }

    @Test
    void insertsAreCounted() {
        assertEquals(5, trie.size());
        assertTrue(trie.contains("apple"));
        assertFalse(trie.contains("appl"));   // a prefix is not a stored word
    }

    @Test
    void prefixSearchReturnsOnlyMatchingTerms() {
        List<SearchTerm> results = trie.searchByPrefix("app", 10);

        assertEquals(4, results.size());
        assertTrue(results.stream().allMatch(t -> t.getTerm().startsWith("app")));
    }

    @Test
    void resultsAreOrderedByFrequencyDescending() {
        List<SearchTerm> results = trie.searchByPrefix("app", 10);

        assertEquals("apple", results.get(0).getTerm());
        assertEquals("app store", results.get(1).getTerm());
        assertEquals("application", results.get(2).getTerm());
        assertEquals("appointment", results.get(3).getTerm());
    }

    @Test
    void topKIsRespected() {
        List<SearchTerm> results = trie.searchByPrefix("app", 2);

        assertEquals(2, results.size());
        assertEquals("apple", results.get(0).getTerm());
        assertEquals("app store", results.get(1).getTerm());
    }

    @Test
    void unknownPrefixReturnsEmptyList() {
        assertTrue(trie.searchByPrefix("zzz", 10).isEmpty());
    }

    @Test
    void prefixSearchIsCaseInsensitive() {
        List<SearchTerm> results = trie.searchByPrefix("APP", 10);

        assertEquals(4, results.size());
        assertEquals("apple", results.get(0).getTerm());
    }

    @Test
    void exactWordIsIncludedInItsOwnPrefixResults() {
        List<SearchTerm> results = trie.searchByPrefix("apple", 10);

        assertEquals(1, results.size());
        assertEquals("apple", results.get(0).getTerm());
    }

    @Test
    void duplicateInsertsAccumulateFrequency() {
        trie.insert("banana", 1000);

        assertEquals(5, trie.size());   // still one distinct term
        assertEquals(5000, trie.searchByPrefix("banana", 1).get(0).getFrequency());
    }

    @Test
    void blankPrefixAndNonPositiveLimitReturnEmpty() {
        assertTrue(trie.searchByPrefix("", 10).isEmpty());
        assertTrue(trie.searchByPrefix(null, 10).isEmpty());
        assertTrue(trie.searchByPrefix("app", 0).isEmpty());
    }
}
