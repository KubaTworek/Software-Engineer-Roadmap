package com.example.paymentsystem.reconciliation;

import com.example.paymentsystem.payment.PaymentProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementReportTest {
    @Test
    void reportRequiresARealCompletenessWindow() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertThatThrownBy(() -> new SettlementReport("id", PaymentProvider.PAYU_MOCK, "external",
                now, now, "checksum"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
