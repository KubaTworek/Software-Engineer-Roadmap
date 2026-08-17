package com.example.urlshortener.exception;

public class CustomAliasAlreadyExistsException extends RuntimeException {
    public CustomAliasAlreadyExistsException(String alias) {
        super("Custom alias already exists: " + alias);
    }
}
