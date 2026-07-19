package com.example.urlshortener.repository;

import com.example.urlshortener.model.urlMapping;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface urlRepository extends MongoRepository<urlMapping,String> {

    Optional<urlMapping> findByShortUrl(String shortUrl);

    Optional<urlMapping> findByLongUrl(String longUrl);
}
