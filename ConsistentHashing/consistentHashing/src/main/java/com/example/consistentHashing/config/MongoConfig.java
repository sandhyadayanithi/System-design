package com.example.consistentHashing.config;

import com.mongodb.client.MongoClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.context.annotation.Primary;

@Configuration
public class MongoConfig {

    @Primary
    @Bean(name = "mongo1")
    public MongoTemplate mongo1() {
        return new MongoTemplate(
                MongoClients.create("mongodb://localhost:27017"),
                "studentdb1");
    }

    @Bean(name = "mongo2")
    public MongoTemplate mongo2() {
        return new MongoTemplate(
                MongoClients.create("mongodb://localhost:27018"),
                "studentdb2");
    }

    @Bean(name = "mongo3")
    public MongoTemplate mongo3() {
        return new MongoTemplate(
                MongoClients.create("mongodb://localhost:27019"),
                "studentdb3");
    }

    @Bean(name = "mongo4")
    public MongoTemplate mongo4() {
        return new MongoTemplate(
                MongoClients.create("mongodb://localhost:27020"),
                "studentdb4");
    }
}