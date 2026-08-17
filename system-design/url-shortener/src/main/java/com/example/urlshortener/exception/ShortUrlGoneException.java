package com.example.urlshortener.exception;

public class ShortUrlGoneException extends RuntimeException {
    public ShortUrlGoneException(String shortCode, String reason) {
        super("Short URL is no longer available: " + shortCode + " (" + reason + ")");
    }
}
