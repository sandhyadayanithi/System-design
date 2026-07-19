package com.example.urlshortener.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Random;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.example.urlshortener.model.urlMapping;
import com.example.urlshortener.repository.urlRepository;

@Service
public class urlService {
    private final urlRepository repo;
    private final RedisTemplate<String,String> redisTemplate;

    public urlService(urlRepository repo,RedisTemplate<String,String> redisTemplate)
    {
        this.repo=repo;
        this.redisTemplate=redisTemplate;
    }

    public String createShortUrl (String longUrl)
    {
        System.out.println("Inside createShortUrl()");
        Optional<urlMapping> url = repo.findByLongUrl(longUrl);

        if(url.isPresent())
        {
            return url.get().getShortUrl();
        }

        String shortUrl = generateShortUrl(longUrl);

        while( repo.findByShortUrl(shortUrl).isPresent()){
            shortUrl += randomCharacter();
        }

        urlMapping mapping = new urlMapping(null,longUrl,shortUrl);
        repo.save(mapping);
        return shortUrl;
    }

    public String getLongUrl(String shortUrl)
    {

        String cachedUrl = redisTemplate.opsForValue().get(shortUrl);

        if (cachedUrl!=null){
            System.out.println("CACHE HIT");
            return cachedUrl;
        }

        System.out.println("CACHE MISS");

        Optional<urlMapping> mapping = repo.findByShortUrl(shortUrl);

        if (mapping.isPresent()) {

            String longUrl = mapping.get().getLongUrl();
            redisTemplate.opsForValue().set(shortUrl, longUrl);

            return longUrl;
        }

        return null;
    }

    private String generateShortUrl(String longUrl)
    {
        try{

            MessageDigest md = MessageDigest.getInstance("SHA-256");

            byte[] hash = md.digest(longUrl.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();

            for (byte b : hash){
                sb.append(String.format("%02x",b));
            }

            return sb.substring(0,6);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private char randomCharacter(){

        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        return chars.charAt(random.nextInt(chars.length()));

    }

}