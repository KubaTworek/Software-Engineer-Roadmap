package com.example.urlshortener.exception;

public class OwnershipDeniedException extends RuntimeException {
    public OwnershipDeniedException() { super("The authenticated principal does not own this URL"); }
}
