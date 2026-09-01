package pl.jakubtworek.backend_engineering.stage_2.block_b.websocket;

public final class ResumeWindowExceededException extends RuntimeException {
    public ResumeWindowExceededException(String message) {
        super(message);
    }
}
