package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.replay;

/**
 * Temporary failure from an external payment provider.
 *
 * This kind of error should usually be retried.
 */
public class TemporaryPaymentProviderException extends RuntimeException {

    public TemporaryPaymentProviderException(String message) {
        super(message);
    }
}