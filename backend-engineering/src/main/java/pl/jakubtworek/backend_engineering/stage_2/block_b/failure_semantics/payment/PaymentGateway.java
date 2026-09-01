package pl.jakubtworek.backend_engineering.stage_2.block_b.failure_semantics.payment;

@FunctionalInterface
public interface PaymentGateway {

    GatewayDecision authorize(PaymentCommand command);
}
