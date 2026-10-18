package com.example.paymentsystem.psp;

import com.example.paymentsystem.payment.PaymentProvider;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Contract every real PSP adapter should satisfy before joining the registry. */
class PaymentProviderClientContractTest {
    private final PaymentProviderClient client = new MockProviderClient(PaymentProvider.PAYU_MOCK);

    @Test
    void sameIdempotencyKeyProducesTheSameProviderReference() {
        PspPaymentRequest first = request("stable-key");
        PspPaymentRequest retry = new PspPaymentRequest(UUID.randomUUID(), "another-local-attempt",
                10_000, "PLN", "stable-key");

        assertThat(client.createPayment(first).providerPaymentId())
                .isEqualTo(client.createPayment(retry).providerPaymentId());
    }

    @Test
    void connectTimeoutAndRateLimitAreSafeToRetry() {
        assertThatThrownBy(() -> client.createPayment(request("simulate-connect-timeout-1")))
                .isInstanceOfSatisfying(ProviderCallException.class,
                        failure -> assertThat(failure.certainty()).isEqualTo(ProviderOutcomeCertainty.NOT_SENT));

        assertThatThrownBy(() -> client.createPayment(request("simulate-rate-limit-1")))
                .isInstanceOfSatisfying(ProviderCallException.class, failure -> {
                    assertThat(failure.safeToRetry()).isTrue();
                    assertThat(failure.retryAfter()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Test
    void responseTimeoutAndUnknownSchemaHaveAmbiguousOutcome() {
        assertThatThrownBy(() -> client.createPayment(request("simulate-timeout-1")))
                .isInstanceOfSatisfying(ProviderCallException.class,
                        failure -> assertThat(failure.certainty()).isEqualTo(ProviderOutcomeCertainty.UNKNOWN));
        assertThatThrownBy(() -> client.createPayment(request("simulate-incompatible-response-1")))
                .isInstanceOfSatisfying(ProviderCallException.class,
                        failure -> assertThat(failure.safeToRetry()).isFalse());
    }

    private PspPaymentRequest request(String key) {
        return new PspPaymentRequest(UUID.randomUUID(), "order", 10_000, "PLN", key);
    }
}
