package com.example.ratelimiter.config;

public class ConfigVersionConflictException extends RuntimeException {
    public ConfigVersionConflictException(long expected, long actual) {
        super("Configuration version mismatch: expected=" + expected + ", actual=" + actual);
    }
}
