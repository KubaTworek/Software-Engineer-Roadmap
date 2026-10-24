package pl.jakubtworek.lld.atm;

import pl.jakubtworek.lld.change.BoundedChangePlanner;
import java.util.*;

/**
 * Bankomat jest wariantem tego samego zadania co automat: ograniczona reszta.
 * Adapter skaluje kwotę i nominały do jednostek 10 PLN, przez co DP ma tylko
 * 200 stanów zamiast 200000 groszy. Reguły sesji pozostają poza planerem.
 */
public final class BoundedCashPlanner {

    private static final Banknote[] NOTES = Arrays.stream(Banknote.values())
            .sorted(Comparator.comparingInt(Banknote::grosz).reversed()).toArray(Banknote[]::new);
    private static final int[] VALUES = Arrays.stream(NOTES).mapToInt(note -> note.grosz() / 1000).toArray();
    private final BoundedChangePlanner planner = new BoundedChangePlanner(200);

    public Optional<CashBundle> plan(Money amount, CashInventory available) {
        WithdrawalRules.requireAllowed(amount);
        Objects.requireNonNull(available);
        int[] counts = Arrays.stream(NOTES).mapToInt(available::count).toArray();
        return planner.plan(Math.toIntExact(amount.grosz() / 1000), VALUES, counts).map(plan -> {
            var result = new EnumMap<Banknote, Integer>(Banknote.class);
            for (int i = 0; i < plan.length; i++) {
                if (plan[i] > 0) {
                    result.put(NOTES[i], plan[i]);
                }
            }
            return new CashBundle(result);
        });
    }
}
