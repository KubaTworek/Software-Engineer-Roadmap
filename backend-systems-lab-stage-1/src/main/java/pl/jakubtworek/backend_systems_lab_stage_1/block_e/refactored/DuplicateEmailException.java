package pl.jakubtworek.backend_systems_lab_stage_1.block_e.refactored;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}
