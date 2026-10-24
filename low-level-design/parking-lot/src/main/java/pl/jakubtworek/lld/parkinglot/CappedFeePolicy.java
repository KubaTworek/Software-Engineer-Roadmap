package pl.jakubtworek.lld.parkinglot;

import java.util.Objects;

/**
 * Decorator: realizuje ten sam kontrakt co opakowany cennik, ale ogranicza
 * poprawnie obliczoną należność za CAŁY postój. Nie jest to limit na dobę:
 * nie dzielimy pobytu według kalendarza, 24 godzin ani strefy czasowej.
 *
 * Najpierw dokładnie raz wywołujemy delegata z tym samym kontekstem, potem
 * stosujemy min(wynik, limit). Nie kopiujemy zasad godzin ani okresu gratis.
 * Można opakować HourlyFeePolicy, FlatFeePolicy albo inny CappedFeePolicy.
 *
 * Cel edukacyjny: Strategy wybiera sposób wyceny, Decorator dokłada regułę
 * do istniejącego sposobu. Limit NIE maskuje null, awarii ani przepełnienia
 * delegata, nawet jeśli wynosi zero. Nieudana wycena nie staje się sukcesem.
 */
public final class CappedFeePolicy implements FeePolicy {

    private final FeePolicy delegate;
    private final Money maximum;

    public CappedFeePolicy(FeePolicy delegate, Money maximum) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.maximum = Objects.requireNonNull(maximum, "maximum");
    }

    @Override
    public Money calculate(PricingContext context) {
        Money fee = Objects.requireNonNull(delegate.calculate(Objects.requireNonNull(context, "context")),
                "delegate fee");
        return fee.grosz() > maximum.grosz() ? maximum : fee;
    }
}
