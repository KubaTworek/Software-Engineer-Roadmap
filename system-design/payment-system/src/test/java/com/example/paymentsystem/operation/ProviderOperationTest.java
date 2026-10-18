package com.example.paymentsystem.operation;

import com.example.paymentsystem.payment.PaymentProvider;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderOperationTest {
    @Test
    void timeoutAfterDispatchRequiresReconciliationInsteadOfBlindRetry() {
        ProviderOperation operation = operation();
        operation.start();
        operation.markUnknown(new RuntimeException("read timeout"));

        assertThat(operation.getStatus()).isEqualTo(ProviderOperationStatus.UNKNOWN);
        assertThatThrownBy(operation::start).isInstanceOf(RuntimeException.class);

        operation.succeed("provider-refund-17");
        assertThat(operation.getStatus()).isEqualTo(ProviderOperationStatus.SUCCEEDED);
    }

    @Test
    void preDispatchFailureIsTerminalButHasNoAmbiguousExternalEffect() {
        ProviderOperation operation = operation();
        operation.start();
        operation.failBeforeDispatch(new RuntimeException("connect timeout"));

        assertThat(operation.getStatus()).isEqualTo(ProviderOperationStatus.FAILED);
        assertThat(operation.getProviderReference()).isNull();
    }

    private ProviderOperation operation() {
        return new ProviderOperation(UUID.randomUUID(), ProviderOperationType.REFUND,
                PaymentProvider.PAYU_MOCK, "refund-key");
    }
}
