package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.overload;

public final class LoadShedException extends RuntimeException {

    public LoadShedException(String message, Throwable cause) {
        super(message, cause);
    }
}
