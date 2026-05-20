package pl.jakubtworek.backend_engineering.stage_1.block_c.exception;

/**
 * Custom exception used when requested resource does not exist.
 *
 * It should be mapped to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}