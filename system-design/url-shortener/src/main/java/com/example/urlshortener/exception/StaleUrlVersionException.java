package com.example.urlshortener.exception;

public class StaleUrlVersionException extends RuntimeException {
    public StaleUrlVersionException(long expected, long actual) {
        super("URL version is stale: expected " + expected + ", actual " + actual);
    }
}
