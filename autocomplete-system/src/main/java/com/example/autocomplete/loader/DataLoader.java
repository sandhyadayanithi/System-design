package com.example.autocomplete.loader;

import com.example.autocomplete.trie.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads the "term,frequency" CSV dataset into the trie once, at application startup.
 */
@Component
public class DataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final Trie trie;
    private final ResourceLoader resourceLoader;
    private final String datasetLocation;

    public DataLoader(Trie trie,
                      ResourceLoader resourceLoader,
                      @Value("${autocomplete.dataset:classpath:data/search_terms.csv}") String datasetLocation) {
        this.trie = trie;
        this.resourceLoader = resourceLoader;
        this.datasetLocation = datasetLocation;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Resource resource = resourceLoader.getResource(datasetLocation);
        if (!resource.exists()) {
            log.error("Dataset not found at {} — the Trie will be empty", datasetLocation);
            return;
        }

        int loaded = 0;
        int skipped = 0;
        long start = System.currentTimeMillis();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // tolerate an optional "term,frequency" header row
                if (firstLine) {
                    firstLine = false;
                    if (line.toLowerCase().startsWith("term,")) {
                        continue;
                    }
                }

                int comma = line.lastIndexOf(',');
                if (comma <= 0) {
                    skipped++;
                    continue;
                }
                String term = line.substring(0, comma).trim();
                try {
                    long frequency = Long.parseLong(line.substring(comma + 1).trim());
                    trie.insert(term, frequency);
                    loaded++;
                } catch (NumberFormatException e) {
                    skipped++;
                }
            }
        }

        log.info("Dataset loaded into Trie: {} terms from {} in {} ms ({} malformed rows skipped)",
                loaded, datasetLocation, System.currentTimeMillis() - start, skipped);
    }
}
