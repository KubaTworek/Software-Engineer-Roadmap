package pl.jakubtworek.backend_systems_lab_stage_1.block_c.mvc;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler.
 *
 * @ControllerAdvice / @RestControllerAdvice participates in MVC pipeline
 * when controller throws an exception.
 */
@RestControllerAdvice
public class MvcExceptionHandler {

    /**
     * Handles domain exception and maps it to HTTP 404.
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleNotFound(
            UserNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }

    /**
     * Handles validation errors from @Valid @RequestBody.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body("Request validation failed");
    }

    /**
     * Fallback handler.
     *
     * Do not expose stack trace or internal error details to API clients.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(
            Exception exception
    ) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Unexpected server error");
    }
}