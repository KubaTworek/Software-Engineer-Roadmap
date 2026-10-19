package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.coordination;

public final class StaleFencingTokenException extends IllegalStateException {

    public StaleFencingTokenException(long candidate, long highestAccepted) {
        super("fencing token %d is older than %d".formatted(candidate, highestAccepted));
    }
}
