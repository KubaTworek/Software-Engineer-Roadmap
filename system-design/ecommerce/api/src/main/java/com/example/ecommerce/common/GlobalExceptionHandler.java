package com.example.ecommerce.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jeden kontrakt błędu dla całego publicznego API. RFC 9457 Problem Details
 * oddziela stabilny typ/kod problemu od tekstu przeznaczonego dla człowieka.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(
            ApiException ex,
            HttpServletRequest request
    ) {
        return response(ex.getStatus(), ex.getMessage(), codeFor(ex.getStatus()), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<Map<String, String>> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return response(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_FAILED", request, fields);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        // Szczegóły wyjątku powinny trafić do logu powiązanego correlationId,
        // nigdy do odpowiedzi, która ujawniałaby implementację lub sekrety.
        log.error("Unhandled request failure path={}", request.getRequestURI(), ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", "INTERNAL_ERROR", request, List.of());
    }

    private ResponseEntity<ProblemDetail> response(
            HttpStatus status,
            String detail,
            String code,
            HttpServletRequest request,
            List<Map<String, String>> errors
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://example.com/problems/" + code.toLowerCase().replace('_', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", clock.instant());
        problem.setProperty("correlationId", correlationId(request));
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return ResponseEntity.status(status).body(problem);
    }

    private String correlationId(HttpServletRequest request) {
        String supplied = request.getHeader("X-Correlation-ID");
        return supplied == null || supplied.isBlank() ? UUID.randomUUID().toString() : supplied;
    }

    private String codeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "INVALID_REQUEST";
            case FORBIDDEN -> "ACCESS_DENIED";
            case NOT_FOUND -> "RESOURCE_NOT_FOUND";
            case CONFLICT -> "BUSINESS_CONFLICT";
            default -> "REQUEST_FAILED";
        };
    }

    private Map<String, String> toFieldError(FieldError error) {
        return Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage()));
    }
}
