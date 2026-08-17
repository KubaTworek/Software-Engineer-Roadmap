package com.example.videostreaming.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> handle(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ApiError.of(ex.getStatusCode().value(), ex.getReason(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handle(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(ApiError.of(400, "Bad Request", message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handle(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(403, "Forbidden", ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ApiError> handle(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handle(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.internalServerError().body(ApiError.of(500, "Internal Server Error", "Unexpected server error"));
    }
}
