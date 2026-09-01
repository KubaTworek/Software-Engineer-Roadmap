package pl.jakubtworek.backend_engineering.stage_2.block_b.failure_semantics.lease;

public final class LeaseUnavailableException extends RuntimeException {

    public LeaseUnavailableException(String resourceId, String currentOwnerId) {
        super("Resource %s is still leased by %s".formatted(resourceId, currentOwnerId));
    }
}
