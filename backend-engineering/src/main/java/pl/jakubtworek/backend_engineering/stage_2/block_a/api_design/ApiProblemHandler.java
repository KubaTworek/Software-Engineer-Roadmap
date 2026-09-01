package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = OrderApiController.class)
public final class ApiProblemHandler {

    @ExceptionHandler(ApiFailure.class)
    ResponseEntity<ProblemDetail> handleApiFailure(ApiFailure failure) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(failure.status()), failure.getMessage());
        problem.setTitle(titleFor(failure.status()));
        problem.setType(URI.create("https://example.com/problems/" + failure.code()));
        problem.setProperty("code", failure.code());
        return ResponseEntity.status(failure.status()).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setType(URI.create("https://example.com/problems/validation_failed"));
        problem.setProperty("code", "validation_failed");
        List<Violation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(ApiProblemHandler::toViolation)
                .toList();
        problem.setProperty("violations", violations);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ProblemDetail> handleMalformedRequest(Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request cannot be parsed or is incomplete");
        problem.setTitle("Invalid request");
        problem.setType(URI.create("https://example.com/problems/invalid_request"));
        problem.setProperty("code", "invalid_request");
        return ResponseEntity.badRequest().body(problem);
    }

    private static Violation toViolation(FieldError error) {
        return new Violation(error.getField(), error.getDefaultMessage());
    }

    private static String titleFor(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved == null ? "Request failed" : resolved.getReasonPhrase();
    }

    public record Violation(String field, String message) {
    }
}
