package com.example.consistentHashing;

import com.example.consistentHashing.service.ConsistentHashService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ConsistentHashingApplication implements CommandLineRunner {

    @Autowired
    private ConsistentHashService hashService;

    public static void main(String[] args) {
        SpringApplication.run(ConsistentHashingApplication.class, args);
    }

    @Override
    public void run(String... args) {

        hashService.printRing();

    }
}