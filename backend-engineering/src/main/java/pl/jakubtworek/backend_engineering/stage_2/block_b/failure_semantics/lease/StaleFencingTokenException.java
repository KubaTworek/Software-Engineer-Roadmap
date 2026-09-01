package pl.jakubtworek.backend_engineering.stage_2.block_b.failure_semantics.lease;

public final class StaleFencingTokenException extends RuntimeException {

    public StaleFencingTokenException(long received, long lastAccepted) {
        super("Fencing token %d is not newer than last accepted token %d"
                .formatted(received, lastAccepted));
    }
}
