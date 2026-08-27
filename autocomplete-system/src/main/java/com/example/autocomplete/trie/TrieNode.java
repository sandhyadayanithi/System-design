package com.example.autocomplete.trie;

import java.util.HashMap;
import java.util.Map;

/**
 * One character position in the prefix tree.
 * Children are keyed by the next character, so walking a prefix is O(prefix length).
 */
public class TrieNode {

    private final Map<Character, TrieNode> children = new HashMap<>();
    private boolean endOfWord;
    private long frequency;

    public Map<Character, TrieNode> getChildren() {
        return children;
    }

    public TrieNode childFor(char c) {
        return children.get(c);
    }

    public TrieNode childOrCreate(char c) {
        return children.computeIfAbsent(c, k -> new TrieNode());
    }

    public boolean isEndOfWord() {
        return endOfWord;
    }

    public void setEndOfWord(boolean endOfWord) {
        this.endOfWord = endOfWord;
    }

    public long getFrequency() {
        return frequency;
    }

    public void addFrequency(long delta) {
        this.frequency += delta;
    }
}
