package pl.jakubtworek.cloudarchitecture.controller;

import pl.jakubtworek.cloudarchitecture.service.RateLimitExceededException;
import pl.jakubtworek.cloudarchitecture.service.ResourceNotFoundException;
import pl.jakubtworek.cloudarchitecture.service.IdempotencyConflictException;
import pl.jakubtworek.cloudarchitecture.service.IdempotencyInProgressException;
import pl.jakubtworek.cloudarchitecture.service.DependencyUnavailableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * Centralized exception mapping.
 *
 * Controllers stay focused on use cases, while this class converts domain
 * errors into consistent HTTP responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> tooManyRequests(RateLimitExceededException ex) {
        return ResponseEntity.status(429).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({IdempotencyConflictException.class, IdempotencyInProgressException.class})
    public ResponseEntity<Map<String, String>> idempotencyConflict(RuntimeException ex) {
        return ResponseEntity.status(409).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    public ResponseEntity<Map<String, String>> dependencyUnavailable(DependencyUnavailableException ex) {
        return ResponseEntity.status(503).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> genericError(Exception ex) {
        return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
    }
}
