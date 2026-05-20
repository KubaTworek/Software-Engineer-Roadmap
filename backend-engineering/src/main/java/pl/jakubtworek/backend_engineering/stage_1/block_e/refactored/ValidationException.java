package pl.jakubtworek.backend_engineering.stage_1.block_e.refactored;

public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}