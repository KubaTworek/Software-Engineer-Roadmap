package com.example.paymentsystem.common;

import com.example.paymentsystem.idempotency.IdempotencyConflictException;
import com.example.paymentsystem.payment.PaymentException;
import com.example.paymentsystem.psp.ProviderUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(PaymentException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse payment(PaymentException e) {
        return error(HttpStatus.CONFLICT, e.getMessage(), Map.of());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse idem(IdempotencyConflictException e) {
        return error(HttpStatus.CONFLICT, e.getMessage(), Map.of());
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorResponse provider(ProviderUnavailableException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse validation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    private ApiErrorResponse error(HttpStatus status, String message, Map<String, String> validationErrors) {
        return new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, validationErrors);
    }
}
