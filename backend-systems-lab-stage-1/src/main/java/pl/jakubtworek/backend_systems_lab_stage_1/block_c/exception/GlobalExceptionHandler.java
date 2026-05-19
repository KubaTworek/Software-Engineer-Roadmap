package pl.jakubtworek.backend_systems_lab_stage_1.block_c.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;

/**
 * Global exception handler for REST controllers.
 *
 * @RestControllerAdvice combines:
 * - @ControllerAdvice
 * - @ResponseBody
 *
 * It allows mapping exceptions to consistent HTTP responses
 * in one central place.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles entity/resource not found errors.
     *
     * Example:
     * GET /users/999 -> 404 NOT_FOUND
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException exception
    ) {
        ApiError error = ApiError.of(
                "NOT_FOUND",
                exception.getMessage(),
                HttpStatus.NOT_FOUND.value()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(error);
    }

    /**
     * Handles invalid business input.
     *
     * Example:
     * invalid operation requested by the client.
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(
            BadRequestException exception
    ) {
        ApiError error = ApiError.of(
                "BAD_REQUEST",
                exception.getMessage(),
                HttpStatus.BAD_REQUEST.value()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    /**
     * Handles business rule violations.
     *
     * Example:
     * trying to cancel an already completed order.
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiError> handleBusinessRuleViolation(
            BusinessRuleViolationException exception
    ) {
        ApiError error = ApiError.of(
                "BUSINESS_RULE_VIOLATION",
                exception.getMessage(),
                HttpStatus.CONFLICT.value()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error);
    }

    /**
     * Handles validation errors from @Valid request bodies.
     *
     * Triggered when controller method has:
     *
     * public ResponseEntity<?> create(@Valid @RequestBody CreateUserRequest request)
     *
     * and validation fails.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        List<FieldErrorResponse> fieldErrors =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(fieldError -> new FieldErrorResponse(
                                fieldError.getField(),
                                fieldError.getDefaultMessage()
                        ))
                        .toList();

        ApiError error = ApiError.withFieldErrors(
                "VALIDATION_ERROR",
                "Request validation failed",
                HttpStatus.BAD_REQUEST.value(),
                fieldErrors
        );

        return ResponseEntity
                .badRequest()
                .body(error);
    }

    /**
     * Handles validation errors from request parameters or path variables.
     *
     * Example:
     * @RequestParam @Min(1) Integer page
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        List<FieldErrorResponse> fieldErrors =
                exception.getConstraintViolations()
                        .stream()
                        .map(violation -> new FieldErrorResponse(
                                violation.getPropertyPath().toString(),
                                violation.getMessage()
                        ))
                        .toList();

        ApiError error = ApiError.withFieldErrors(
                "CONSTRAINT_VIOLATION",
                "Request parameter validation failed",
                HttpStatus.BAD_REQUEST.value(),
                fieldErrors
        );

        return ResponseEntity
                .badRequest()
                .body(error);
    }

    /**
     * Handles authorization errors.
     *
     * Usually Spring Security handles 401/403 earlier,
     * but this handler can be useful for method-level security.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException exception
    ) {
        ApiError error = ApiError.of(
                "ACCESS_DENIED",
                "You do not have permission to perform this action",
                HttpStatus.FORBIDDEN.value()
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(error);
    }

    /**
     * Fallback handler for unexpected exceptions.
     *
     * Important:
     * - log full exception on the server,
     * - return generic message to the client,
     * - do not expose stack trace or internal details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(
            Exception exception
    ) {
        log.error("Unexpected server error", exception);

        ApiError error = ApiError.of(
                "SERVER_ERROR",
                "Unexpected server error occurred",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }

    /**
     * Alternative approach available in Spring 6+.
     *
     * ProblemDetail follows RFC 7807 standard.
     * Spring can serialize it as application/problem+json.
     *
     * This method is shown as an example.
     * In real code, choose either ApiError or ProblemDetail
     * to keep API responses consistent.
     */
    public ProblemDetail createProblemDetailExample(
            RuntimeException exception
    ) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        problemDetail.setType(URI.create("https://api.example.com/errors/bad-request"));
        problemDetail.setTitle("Bad Request");
        problemDetail.setDetail(exception.getMessage());
        problemDetail.setProperty("code", "BAD_REQUEST");

        return problemDetail;
    }
}