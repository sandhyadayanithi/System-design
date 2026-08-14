package com.example.ratelimiter.exception;

public class MissingClientIdException extends RuntimeException {
    public MissingClientIdException(String message) {
        super(message);
    }
}
