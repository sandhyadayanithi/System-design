package com.example.urlshortener.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection="urls")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class urlMapping {

    @Id
    private String id;
    private String longUrl;
    private String shortUrl;

}
