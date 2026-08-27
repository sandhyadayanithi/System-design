package com.example.autocomplete.config;

import com.example.autocomplete.trie.Trie;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The trie is a single application-wide, in-memory index shared by all requests.
 */
@Configuration
public class TrieConfig {

    @Bean
    public Trie trie() {
        return new Trie();
    }
}
