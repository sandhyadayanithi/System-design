package com.example.urlshortener.service;

import com.example.urlshortener.model.urlMapping;
import com.example.urlshortener.repository.urlRepository;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Random;

@Service
public class urlService {
    private final urlRepository repo;

    public urlService(urlRepository repo)
    {
        this.repo=repo;
    }

    public String createShortUrl (String longUrl)
    {
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
        return repo.findByLongUrl(shortUrl).map(urlMapping::getLongUrl).orElse(null);

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



