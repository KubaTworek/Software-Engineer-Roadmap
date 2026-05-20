package pl.jakubtworek.backend_engineering.stage_1.block_c.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Alternative global exception handler using ProblemDetail.
 *
 * ProblemDetail is a standard error representation
 * introduced in Spring 6 based on RFC 7807.
 *
 * Do not use both ApiError and ProblemDetail styles
 * inconsistently in one API unless there is a clear reason.
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler {

    /**
     * Maps ResourceNotFoundException to ProblemDetail response.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(
            ResourceNotFoundException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.NOT_FOUND,
                        exception.getMessage()
                );

        problem.setType(URI.create("https://api.example.com/errors/not-found"));
        problem.setTitle("Resource Not Found");
        problem.setProperty("code", "NOT_FOUND");

        return problem;
    }
}