package com.example.autocomplete.api;

import org.springframework.http.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> missing(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("Missing required parameter: " + e.getParameterName(), Instant.now()));
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Invalid request", Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> generic(Exception e) {
        // Surowy komunikat wyjątku może zawierać nazwę klasy, ścieżkę lub dane
        // wejściowe. Szczegóły należą do logu/trace, nie do publicznego kontraktu.
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal server error", Instant.now()));
    }
}
