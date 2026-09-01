package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
