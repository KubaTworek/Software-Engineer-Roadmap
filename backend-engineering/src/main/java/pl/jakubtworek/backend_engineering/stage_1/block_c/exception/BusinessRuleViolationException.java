package pl.jakubtworek.backend_engineering.stage_1.block_c.exception;

/**
 * Custom exception used when business rule is violated.
 *
 * It should usually be mapped to HTTP 409 Conflict
 * or HTTP 400 Bad Request, depending on the case.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}