package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Global exception handler.
 *
 * @ControllerAdvice / @RestControllerAdvice participates in MVC pipeline
 * when controller throws an exception.
 */
import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice(assignableTypes = UserRestController.class)
public class MvcExceptionHandler {

    /**
     * Handles domain exception and maps it to HTTP 404.
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleNotFound(
            UserNotFoundException exception
    ) {
        return problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", exception.getMessage());
    }

    /**
     * Handles validation errors from @Valid @RequestBody.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(
            MethodArgumentNotValidException exception
    ) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request body validation failed"
        );
        Map<String, String> fields = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        field -> field.getField(),
                        field -> field.getDefaultMessage() == null
                                ? "Invalid value"
                                : field.getDefaultMessage(),
                        (first, ignored) -> first
                ));
        problem.setProperty("fields", fields);
        return problem;
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(IdempotencyConflictException exception) {
        return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", exception.getMessage());
    }

    @ExceptionHandler(PreconditionFailedException.class)
    public ProblemDetail handlePreconditionFailed(PreconditionFailedException exception) {
        return problem(HttpStatus.PRECONDITION_FAILED, "STALE_RESOURCE_VERSION", exception.getMessage());
    }

    @ExceptionHandler(PreconditionRequiredException.class)
    public ProblemDetail handlePreconditionRequired(PreconditionRequiredException exception) {
        return problem(HttpStatus.PRECONDITION_REQUIRED, "IF_MATCH_REQUIRED", exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingRequestHeaderException.class})
    public ProblemDetail handleInvalidProtocolInput(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_HTTP_PRECONDITION", exception.getMessage());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleMethodValidation(HandlerMethodValidationException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request parameter validation failed"
        );
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://api.example.com/problems/" + code.toLowerCase()));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }
}
