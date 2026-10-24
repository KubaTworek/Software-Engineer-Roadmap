package pl.jakubtworek.lld.vendingmachine;

import pl.jakubtworek.lld.change.BoundedChangePlanner;
import java.util.*;

/**
 * Adapter monet do wspólnego ograniczonego planera. Jednostką jest grosz.
 * Odpowiada za kontrakt ChangePolicy i tłumaczenie enumów; algorytm DP jest
 * zaimplementowany tylko raz, razem z wariantem banknotowym w tym module.
 */
public final class MinimumCoinChangePlanner implements ChangePolicy {

    private static final Coin[] COINS = Coin.values();
    private static final int[] VALUES = Arrays.stream(COINS).mapToInt(Coin::grosz).toArray();
    private final BoundedChangePlanner planner = new BoundedChangePlanner(MAX_AMOUNT_GROSZ);

    @Override
    public Optional<CoinBundle> plan(Money amount, CoinBundle available) {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(available);
        if (amount.grosz() > MAX_AMOUNT_GROSZ) {
            throw new IllegalArgumentException("Kwota przekracza limit reszty");
        }
        int[] counts = Arrays.stream(COINS).mapToInt(available::count).toArray();
        return planner.plan(Math.toIntExact(amount.grosz()), VALUES, counts).map(plan -> {
            var result = new EnumMap<Coin, Integer>(Coin.class);
            for (int i = 0; i < plan.length; i++) {
                if (plan[i] > 0) {
                    result.put(COINS[i], plan[i]);
                }
            }
            return new CoinBundle(result);
        });
    }
}
