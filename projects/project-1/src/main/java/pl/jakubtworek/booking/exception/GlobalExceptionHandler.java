package pl.jakubtworek.booking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.jakubtworek.booking.dto.ErrorResponse;
import pl.jakubtworek.booking.dto.FieldErrorResponse;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(CapacityUnavailableException.class)
    ResponseEntity<ErrorResponse> handleCapacityUnavailable(CapacityUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "CAPACITY_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler({BusinessRuleException.class, IllegalStateException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> handleBusinessRule(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "BUSINESS_RULE_VIOLATION", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldErrorResponse> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                400,
                "VALIDATION_ERROR",
                "Request validation failed",
                fields
        );
        return ResponseEntity.badRequest().body(body);
    }
}
