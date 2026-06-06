package com.example.urlshortener.exception;

public class AdminUnauthorizedException extends RuntimeException {
    public AdminUnauthorizedException() {
        super("Admin token is missing or invalid");
    }
}
