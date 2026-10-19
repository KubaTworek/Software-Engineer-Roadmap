package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

public class PreconditionFailedException extends RuntimeException {

    public PreconditionFailedException(String message) {
        super(message);
    }
}
