package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.broker;

/**
 * Exception thrown when the fake broker is unavailable.
 */
public class BrokerUnavailableException extends RuntimeException {

    public BrokerUnavailableException(String message) {
        super(message);
    }
}