package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation;

/**
 * Application port for the real payment dependency protected by resilience policies.
 */
@FunctionalInterface
public interface PaymentGateway {

    String reserve(String paymentId) throws Exception;
}
