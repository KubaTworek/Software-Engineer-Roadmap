package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

public class PreconditionRequiredException extends RuntimeException {

    public PreconditionRequiredException(String message) {
        super(message);
    }
}
