package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.consumer;

/**
 * Exception used to simulate consumer crashes in tests.
 */
public class SimulatedConsumerCrashException extends RuntimeException {

    public SimulatedConsumerCrashException(String message) {
        super(message);
    }
}