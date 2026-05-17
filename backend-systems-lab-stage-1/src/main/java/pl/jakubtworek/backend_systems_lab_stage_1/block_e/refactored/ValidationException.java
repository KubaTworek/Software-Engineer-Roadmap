package pl.jakubtworek.backend_systems_lab_stage_1.block_e.refactored;

public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}