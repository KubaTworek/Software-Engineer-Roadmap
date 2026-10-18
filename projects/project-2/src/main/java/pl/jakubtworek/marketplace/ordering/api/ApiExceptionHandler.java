package pl.jakubtworek.marketplace.ordering.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.jakubtworek.marketplace.ordering.application.ConcurrentOrderModificationException;

import java.net.URI;
import java.util.NoSuchElementException;

/**
 * Jeden kontrakt błędu oparty o RFC 9457 Problem Details. Klient nie powinien
 * parsować tekstu wyjątku ani otrzymywać stack trace. Status rozróżnia wadliwy
 * request (400), brak zasobu (404), konflikt współbieżności (409) i niespełniony
 * warunek If-Match (412).
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request validation failed");
        problem.setProperty("violations", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList());
        return problem;
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail missing(NoSuchElementException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", "The requested order does not exist");
    }

    @ExceptionHandler(IdempotencyKeyRegistry.IdempotencyKeyConflictException.class)
    ProblemDetail idempotency(IdempotencyKeyRegistry.IdempotencyKeyConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Idempotency key conflict", exception.getMessage());
    }

    @ExceptionHandler(ConcurrentOrderModificationException.class)
    ProblemDetail concurrent(ConcurrentOrderModificationException exception) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail domainConflict(IllegalStateException exception) {
        return problem(HttpStatus.CONFLICT, "Domain conflict", exception.getMessage());
    }

    @ExceptionHandler(PreconditionFailedException.class)
    ProblemDetail precondition(PreconditionFailedException exception) {
        return problem(HttpStatus.PRECONDITION_FAILED, "Precondition failed", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid value", exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://example.local/problems/" + status.value()));
        return problem;
    }

    public static class PreconditionFailedException extends RuntimeException {
        public PreconditionFailedException(long expected, long actual) {
            super("If-Match expected version %d, but current version is %d".formatted(expected, actual));
        }
    }
}
