package pl.jakubtworek.backend_systems_lab_stage_1.block_c.mvc;

/**
 * Custom exception thrown by service layer.
 *
 * It will be handled globally by @RestControllerAdvice.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}