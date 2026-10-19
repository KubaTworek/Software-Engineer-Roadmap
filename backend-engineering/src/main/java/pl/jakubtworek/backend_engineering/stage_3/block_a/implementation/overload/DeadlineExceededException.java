package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.overload;

public final class DeadlineExceededException extends RuntimeException {

    public DeadlineExceededException(String message) {
        super(message);
    }
}
