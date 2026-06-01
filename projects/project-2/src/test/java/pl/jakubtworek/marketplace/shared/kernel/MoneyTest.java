package pl.jakubtworek.marketplace.shared.kernel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void shouldRoundAmountToTwoDecimalPlaces() {
        Money money = Money.of("10.555", "PLN");

        assertThat(money.amount()).isEqualByComparingTo("10.56");
    }

    @Test
    void shouldAddMoneyInTheSameCurrency() {
        Money result = Money.of("10.00", "PLN").add(Money.of("2.50", "PLN"));

        assertThat(result.amount()).isEqualByComparingTo("12.50");
        assertThat(result.currency().getCurrencyCode()).isEqualTo("PLN");
    }

    @Test
    void shouldRejectAddingDifferentCurrencies() {
        assertThatThrownBy(() -> Money.of("10.00", "PLN").add(Money.of("2.50", "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency mismatch");
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> Money.of("-0.01", "PLN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount cannot be negative");
    }

    @Test
    void shouldRejectNegativeMultiplier() {
        assertThatThrownBy(() -> Money.of("10.00", "PLN").multiply(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiplier cannot be negative");
    }
}
