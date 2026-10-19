package pl.jakubtworek.backend_engineering.stage_2.block_a.grpc;

public final class GrpcRetryPolicy {

    private final int maximumAttempts;

    public GrpcRetryPolicy(int maximumAttempts) {
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("maximumAttempts must be positive");
        }
        this.maximumAttempts = maximumAttempts;
    }

    public boolean shouldRetry(StatusCode status, boolean idempotent, int completedAttempts, RpcDeadline deadline) {
        if (!idempotent || deadline.expired() || completedAttempts >= maximumAttempts) {
            return false;
        }
        return status == StatusCode.UNAVAILABLE || status == StatusCode.RESOURCE_EXHAUSTED;
    }

    public enum StatusCode {
        OK,
        INVALID_ARGUMENT,
        NOT_FOUND,
        ABORTED,
        RESOURCE_EXHAUSTED,
        UNAVAILABLE,
        INTERNAL
    }
}
