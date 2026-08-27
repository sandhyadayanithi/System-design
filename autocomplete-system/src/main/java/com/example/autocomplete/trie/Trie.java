package com.example.autocomplete.trie;

import com.example.autocomplete.model.SearchTerm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Prefix tree over the search-term dataset.
 *
 * insert()         -> O(length of the word)
 * searchByPrefix() -> O(length of prefix) to reach the prefix node,
 *                     then a DFS over that subtree to collect candidates,
 *                     then a sort by frequency to pick the top-K.
 */
public class Trie {

    private final TrieNode root = new TrieNode();
    private int wordCount;

    /**
     * Adds a term to the trie. Inserting the same term twice accumulates the
     * frequency rather than overwriting it.
     */
    public synchronized void insert(String word, long frequency) {
        if (word == null) {
            return;
        }
        String normalized = word.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return;
        }

        TrieNode current = root;
        for (char c : normalized.toCharArray()) {
            current = current.childOrCreate(c);
        }
        if (!current.isEndOfWord()) {
            current.setEndOfWord(true);
            wordCount++;
        }
        current.addFrequency(frequency);
    }

    /**
     * Returns the top-K terms beneath the given prefix, most-searched first.
     * An empty list means no term in the dataset starts with that prefix.
     */
    public List<SearchTerm> searchByPrefix(String prefix, int limit) {
        if (prefix == null || limit <= 0) {
            return Collections.emptyList();
        }
        String normalized = prefix.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. walk down to the node that represents the prefix
        TrieNode current = root;
        for (char c : normalized.toCharArray()) {
            current = current.childFor(c);
            if (current == null) {
                return Collections.emptyList();
            }
        }

        // 2. DFS the subtree, collecting every complete word under the prefix
        List<SearchTerm> matches = new ArrayList<>();
        collect(current, new StringBuilder(normalized), matches);

        // 3. rank by search frequency (ties broken alphabetically) and take top-K
        matches.sort(Comparator.comparingLong(SearchTerm::getFrequency).reversed()
                .thenComparing(SearchTerm::getTerm));

        return matches.size() > limit ? new ArrayList<>(matches.subList(0, limit)) : matches;
    }

    private void collect(TrieNode node, StringBuilder buffer, List<SearchTerm> out) {
        if (node.isEndOfWord()) {
            out.add(new SearchTerm(buffer.toString(), node.getFrequency()));
        }
        for (Map.Entry<Character, TrieNode> entry : node.getChildren().entrySet()) {
            buffer.append(entry.getKey());
            collect(entry.getValue(), buffer, out);
            buffer.deleteCharAt(buffer.length() - 1);
        }
    }

    /** True when the exact word exists in the trie (not just as a prefix). */
    public boolean contains(String word) {
        if (word == null) {
            return false;
        }
        TrieNode current = root;
        for (char c : word.trim().toLowerCase().toCharArray()) {
            current = current.childFor(c);
            if (current == null) {
                return false;
            }
        }
        return current.isEndOfWord();
    }

    /** Number of distinct terms stored. */
    public int size() {
        return wordCount;
    }
}
