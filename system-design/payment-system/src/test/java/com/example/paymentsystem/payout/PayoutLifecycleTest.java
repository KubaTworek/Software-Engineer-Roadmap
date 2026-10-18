package com.example.paymentsystem.payout;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayoutLifecycleTest {
    @Test
    void timeoutIsNotFailureAndRequiresReconciliation() {
        PayoutBatch payout = new PayoutBatch(UUID.randomUUID(), 10_000, "PLN", "key");
        payout.start();
        payout.markUnknown();

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.UNKNOWN);
        assertThatThrownBy(payout::markPaid).isInstanceOf(IllegalStateException.class);
        payout.reconcileFailed();
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
    }

    @Test
    void bankReturnDoesNotRewriteTheHistoricalPaidFact() {
        PayoutBatch payout = new PayoutBatch(UUID.randomUUID(), 10_000, "PLN", "key");
        payout.start();
        payout.markPaid("bank-reference");
        payout.returnedByBank();

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.RETURNED);
        assertThat(payout.getProviderPayoutId()).isEqualTo("bank-reference");
    }
}
