package com.example.ecommerce.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        HttpStatus status = ex.getStatus();
        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                List.of()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldErrorItem> fields = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();

        return ResponseEntity.badRequest().body(new ErrorResponse(
                Instant.now(),
                400,
                "Bad Request",
                "Validation failed",
                fields
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
                Instant.now(),
                500,
                "Internal Server Error",
                "Unexpected error",
                List.of()
        ));
    }

    private ErrorResponse.FieldErrorItem toFieldError(FieldError error) {
        return new ErrorResponse.FieldErrorItem(error.getField(), error.getDefaultMessage());
    }
}
