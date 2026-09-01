package pl.jakubtworek.backend_engineering.stage_2.block_b.failure_semantics.payment;

/** The response deadline expired; the remote business effect may still have happened. */
public final class AmbiguousPaymentTimeoutException extends RuntimeException {

    public AmbiguousPaymentTimeoutException(String message) {
        super(message);
    }
}
