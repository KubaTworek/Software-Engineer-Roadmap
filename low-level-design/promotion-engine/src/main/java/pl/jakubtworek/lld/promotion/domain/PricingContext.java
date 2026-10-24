package pl.jakubtworek.lld.promotion.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fakty jednego obliczenia: oryginalny koszyk, segment i przedstawione kupony.
 * Warunki zawsze czytają tę samą wartość, więc wcześniejszy rabat nie odbiera
 * prawa do promocji z progiem wejścia. Kupon to kod, nie dowód jego autoryzacji.
 */
public record PricingContext(Basket basket, CustomerTier tier, Set<String> coupons) {

    public PricingContext {
        Objects.requireNonNull(basket);
        Objects.requireNonNull(tier);
        coupons = Objects.requireNonNull(coupons).stream().map(PricingContext::normalizeCoupon)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static String normalizeCoupon(String text) {
        if (text == null)
            throw new IllegalArgumentException("Brak kodu kuponu");
        String normalized = text.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_-]+"))
            throw new IllegalArgumentException("Niepoprawny kod kuponu");
        return normalized;
    }
}
