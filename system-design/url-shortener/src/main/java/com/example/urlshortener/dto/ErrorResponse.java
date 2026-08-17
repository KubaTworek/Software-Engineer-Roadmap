package com.example.urlshortener.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String, String> validationErrors
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, Map.of());
    }

    public static ErrorResponse validation(int status, String error, String message, String path, Map<String, String> validationErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, validationErrors);
    }
}
