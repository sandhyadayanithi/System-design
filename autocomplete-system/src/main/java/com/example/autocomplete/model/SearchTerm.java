package com.example.autocomplete.model;

/**
 * A single search term together with how often it has been searched.
 * Frequency is what the top-K ranking is based on.
 */
public class SearchTerm {

    private String term;
    private long frequency;

    public SearchTerm() {
        // needed by Jackson when deserializing cached entries out of Redis
    }

    public SearchTerm(String term, long frequency) {
        this.term = term;
        this.frequency = frequency;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public long getFrequency() {
        return frequency;
    }

    public void setFrequency(long frequency) {
        this.frequency = frequency;
    }

    @Override
    public String toString() {
        return term + "(" + frequency + ")";
    }
}
