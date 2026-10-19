package pl.jakubtworek.backend_engineering.stage_2.block_a.graphql;

public final class QueryRejectedException extends RuntimeException {
    public QueryRejectedException(String message) {
        super(message);
    }
}
