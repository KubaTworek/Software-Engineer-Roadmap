package com.example.paymentsystem.fx;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class FxServiceTest {
    private final FxService service = new FxService();

    @Test
    void convertsBetweenCurrenciesWithDifferentMinorUnitScales() {
        // 100 JPY has zero minor digits; 2.70 PLN has two.
        assertThat(service.convert(100, "JPY", "PLN").targetAmount()).isEqualTo(270);
    }

    @Test
    void failsClosedForUnknownRate() {
        assertThatThrownBy(() -> service.convert(100, "PLN", "ZZZ"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quoteCarriesAuditIdentityExpiryAndRoundingPolicy() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        FxQuote quote = new FxService(Clock.fixed(now, ZoneOffset.UTC)).convert(100, "EUR", "PLN");

        assertThat(quote.quoteId()).isNotBlank();
        assertThat(quote.provider()).isEqualTo("EDUCATIONAL_PLN_TABLE");
        assertThat(quote.roundingPolicy()).isEqualTo("HALF_UP");
        assertThat(quote.isExpiredAt(now.plusSeconds(299))).isFalse();
        assertThat(quote.isExpiredAt(now.plusSeconds(300))).isTrue();
    }
}
