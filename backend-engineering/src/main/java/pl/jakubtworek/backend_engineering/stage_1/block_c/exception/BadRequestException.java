package pl.jakubtworek.backend_engineering.stage_1.block_c.exception;

/**
 * Custom exception used when request is logically invalid.
 *
 * It should be mapped to HTTP 400.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}