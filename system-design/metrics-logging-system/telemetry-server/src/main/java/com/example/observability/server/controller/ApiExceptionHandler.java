package com.example.observability.server.controller;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

/** Jednolity, maszynowo czytelny kontrakt błędu zgodny z RFC 9457. */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail responseStatus(ResponseStatusException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                exception.getStatusCode(), exception.getReason() == null ? "request failed" : exception.getReason());
        detail.setType(URI.create("https://example.com/problems/request-rejected"));
        detail.setTitle("Telemetry request rejected");
        return detail;
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    ProblemDetail missing(java.util.NoSuchElementException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.NOT_FOUND, exception.getMessage());
        detail.setType(URI.create("https://example.com/problems/not-found"));
        return detail;
    }
}
