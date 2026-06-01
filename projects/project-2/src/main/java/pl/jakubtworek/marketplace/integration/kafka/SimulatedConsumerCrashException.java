package pl.jakubtworek.marketplace.integration.kafka;

public class SimulatedConsumerCrashException extends RuntimeException {
    public SimulatedConsumerCrashException(String message) {
        super(message);
    }
}
