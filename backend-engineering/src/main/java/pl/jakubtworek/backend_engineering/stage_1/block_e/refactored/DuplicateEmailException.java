package pl.jakubtworek.backend_engineering.stage_1.block_e.refactored;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}
