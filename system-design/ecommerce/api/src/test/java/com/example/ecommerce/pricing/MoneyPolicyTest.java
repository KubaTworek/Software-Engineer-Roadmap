package com.example.ecommerce.pricing;

import com.example.ecommerce.common.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyPolicyTest {
    private final MoneyPolicy money = new MoneyPolicy();

    @Test
    void usesBankersRoundingAtTheMoneyBoundary() {
        assertThat(money.normalize(new BigDecimal("10.005"), "PLN"))
                .isEqualByComparingTo("10.00");
        assertThat(money.normalize(new BigDecimal("10.015"), "PLN"))
                .isEqualByComparingTo("10.02");
    }

    @Test
    void checkoutCannotSilentlyMixCurrencies() {
        assertThatThrownBy(() -> money.requireSingleCurrency(List.of("PLN", "EUR")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("mix currencies");
    }
}
