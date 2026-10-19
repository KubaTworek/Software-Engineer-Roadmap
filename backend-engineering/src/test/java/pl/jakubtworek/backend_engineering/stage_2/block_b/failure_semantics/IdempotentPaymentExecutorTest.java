package pl.jakubtworek.backend_engineering.stage_2.block_b.failure_semantics;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_b.failure_semantics.payment.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotentPaymentExecutorTest {

    @Test
    void shouldRecoverResultAfterResponseWasLostWithoutChargingTwice() {
        PaymentCommand command = command();
        List<UUID> receivedKeys = new ArrayList<>();
        Map<UUID, GatewayDecision> completedOperations = new HashMap<>();

        PaymentGateway gateway = received -> {
            receivedKeys.add(received.idempotencyKey());
            GatewayDecision previous = completedOperations.get(received.idempotencyKey());
            if (previous != null) {
                return previous;
            }

            completedOperations.put(received.idempotencyKey(), GatewayDecision.authorized("PAY-17"));
            throw new AmbiguousPaymentTimeoutException("response was lost after authorization");
        };

        PaymentExecution result = new IdempotentPaymentExecutor(gateway, 2).execute(command);

        assertThat(result.status()).isEqualTo(PaymentExecutionStatus.AUTHORIZED);
        assertThat(result.attempts()).isEqualTo(2);
        assertThat(receivedKeys).containsExactly(command.idempotencyKey(), command.idempotencyKey());
        assertThat(completedOperations).hasSize(1);
    }

    @Test
    void shouldReturnUnknownInsteadOfInventingFailureAfterTimeoutBudgetIsExhausted() {
        PaymentExecution result = new IdempotentPaymentExecutor(
                ignored -> { throw new AmbiguousPaymentTimeoutException("deadline exceeded"); },
                3
        ).execute(command());

        assertThat(result.status()).isEqualTo(PaymentExecutionStatus.UNKNOWN);
        assertThat(result.attempts()).isEqualTo(3);
        assertThat(result.detail()).contains("deadline");
    }

    @Test
    void shouldNotRetryConfirmedBusinessRejectionOrUnknownProgrammingError() {
        List<PaymentCommand> rejectedCalls = new ArrayList<>();
        PaymentExecution rejection = new IdempotentPaymentExecutor(received -> {
            rejectedCalls.add(received);
            return GatewayDecision.rejected("insufficient funds");
        }, 3).execute(command());

        assertThat(rejection.status()).isEqualTo(PaymentExecutionStatus.REJECTED);
        assertThat(rejection.attempts()).isEqualTo(1);
        assertThat(rejectedCalls).hasSize(1);

        List<PaymentCommand> bugCalls = new ArrayList<>();
        IdempotentPaymentExecutor buggyExecutor = new IdempotentPaymentExecutor(received -> {
            bugCalls.add(received);
            throw new IllegalStateException("mapping bug");
        }, 3);

        assertThatThrownBy(() -> buggyExecutor.execute(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mapping bug");
        assertThat(bugCalls).hasSize(1);
    }

    private static PaymentCommand command() {
        return new PaymentCommand(UUID.randomUUID(), "ORDER-42", new BigDecimal("49.99"));
    }
}
