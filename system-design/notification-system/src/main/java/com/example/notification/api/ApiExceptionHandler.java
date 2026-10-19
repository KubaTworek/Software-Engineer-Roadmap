package com.example.notification.api;

import com.example.notification.application.Exceptions;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(Exceptions.NotificationValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(Exceptions.NotificationValidationException ex) {
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(Exceptions.RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(Exceptions.RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error("RATE_LIMIT_EXCEEDED", ex.getMessage()));
    }

    @ExceptionHandler(Exceptions.IdempotencyConflictException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotencyConflict(Exceptions.IdempotencyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("IDEMPOTENCY_CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleBeanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(error("REQUEST_VALIDATION_ERROR", message));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("ILLEGAL_STATE", ex.getMessage()));
    }

    /**
     * Preserve the HTTP semantics chosen by the application boundary.  Without this dedicated
     * branch the generic handler below would accidentally turn tenant-safe 404 responses into
     * 500 responses, leaking neither data nor the correct API contract.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(error("HTTP_" + ex.getStatusCode().value(), ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("INTERNAL_SERVER_ERROR", ex.getMessage()));
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of("timestamp", Instant.now().toString(), "code", code, "message", message);
    }
}
