package com.example.ecommerce.pricing;

import com.example.ecommerce.common.ApiException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Jedno miejsce definiujące skalę i rounding pieniędzy. Przykład obsługuje PLN
 * i typowe waluty dwucyfrowe; pełny system użyłby ISO 4217 i minor units.
 */
@Component
public class MoneyPolicy {
    public BigDecimal normalize(BigDecimal amount, String currency) {
        if (amount == null || currency == null || currency.length() != 3) {
            throw ApiException.badRequest("Amount and three-letter currency are required");
        }
        if (amount.signum() < 0) {
            throw ApiException.badRequest("Money amount cannot be negative");
        }
        int scale = "JPY".equals(currency) ? 0 : 2;
        return amount.setScale(scale, RoundingMode.HALF_EVEN);
    }

    public String requireSingleCurrency(Collection<String> currencies) {
        String currency = currencies.stream().findFirst().orElse("PLN");
        if (currencies.stream().anyMatch(value -> !currency.equals(value))) {
            throw ApiException.conflict("Checkout cannot mix currencies");
        }
        return currency;
    }
}
