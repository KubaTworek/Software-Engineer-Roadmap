package pl.jakubtworek.backend_engineering.stage_2.block_b.failure_semantics.payment;

import java.util.Objects;

/**
 * Retries only the explicitly ambiguous timeout and preserves one idempotency
 * key. Backoff and scheduling already have a separate implementation in the
 * consumer retry laboratory.
 */
public final class IdempotentPaymentExecutor {

    private final PaymentGateway gateway;
    private final int maxAttempts;

    public IdempotentPaymentExecutor(PaymentGateway gateway, int maxAttempts) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
    }

    public PaymentExecution execute(PaymentCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                GatewayDecision decision = gateway.authorize(command);
                if (decision.authorized()) {
                    return result(PaymentExecutionStatus.AUTHORIZED, attempt, command,
                            decision.providerReference());
                }
                return result(PaymentExecutionStatus.REJECTED, attempt, command,
                        decision.rejectionReason());
            } catch (AmbiguousPaymentTimeoutException timeout) {
                if (attempt == maxAttempts) {
                    return result(PaymentExecutionStatus.UNKNOWN, attempt, command,
                            timeout.getMessage());
                }
            }
        }

        throw new IllegalStateException("unreachable retry state");
    }

    private static PaymentExecution result(
            PaymentExecutionStatus status,
            int attempt,
            PaymentCommand command,
            String detail
    ) {
        return new PaymentExecution(status, attempt, command.idempotencyKey(), detail);
    }
}
