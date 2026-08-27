package com.example.autocomplete.config;

import com.example.autocomplete.model.SearchTerm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

/**
 * Cached values are stored as JSON arrays of SearchTerm so they stay readable
 * from redis-cli (GET autocomplete:app:10).
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, List<SearchTerm>> suggestionRedisTemplate(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper();
        CollectionType listType = mapper.getTypeFactory()
                .constructCollectionType(List.class, SearchTerm.class);

        Jackson2JsonRedisSerializer<List<SearchTerm>> valueSerializer =
                new Jackson2JsonRedisSerializer<>(mapper, listType);

        RedisTemplate<String, List<SearchTerm>> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
