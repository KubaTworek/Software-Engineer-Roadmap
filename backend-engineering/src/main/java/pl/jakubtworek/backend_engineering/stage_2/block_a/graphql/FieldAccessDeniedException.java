package pl.jakubtworek.backend_engineering.stage_2.block_a.graphql;

public final class FieldAccessDeniedException extends RuntimeException {
    public FieldAccessDeniedException(String message) {
        super(message);
    }
}
