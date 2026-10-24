package pl.jakubtworek.lld.promotion.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Wyrocznia polityki najpierw enumeruje wszystkie dopuszczalne maski małego
 * grafu, zamiast kopiować pętlę porównań silnika. Wyrocznia alokacji przeszukuje
 * wszystkie podziały i minimalizuje błąd kwadratowy względem udziałów idealnych.
 * Obie są celowo wolniejsze i inaczej zbudowane niż implementacja produkcyjna.
 */
class GeneratedPolicyTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void randomConflictGraphsMatchMaskOracleForBothPolicies() {
        var random = new Random(110022L);
        for (int scenario = 0; scenario < 1000; scenario++) {
            int n = random.nextInt(7), original = random.nextInt(201), cap = random.nextInt(201);
            boolean[][] edges = new boolean[n][n];
            boolean[] exclusive = new boolean[n], eligible = new boolean[n], percent = new boolean[n];
            int[] parameters = new int[n];
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
                if (random.nextInt(4) == 0) {
                    if (random.nextBoolean())
                        edges[i][j] = true;
                    else
                        edges[j][i] = true;
                }
            }
            var promotions = new ArrayList<Promotion>();
            for (int i = 0; i < n; i++) {
                exclusive[i] = random.nextInt(4) == 0;
                eligible[i] = random.nextInt(4) != 0;
                percent[i] = random.nextBoolean();
                parameters[i] = percent[i] ? random.nextInt(10000) + 1 : random.nextInt(200) + 1;
                var excluded = new HashSet<PromotionId>();
                for (int j = 0; j < n; j++) if (edges[i][j])
                    excluded.add(new PromotionId("P" + j));
                boolean qualifies = eligible[i];
                promotions.add(new Promotion(new PromotionId("P" + i), n - i, c -> qualifies,
                        percent[i] ? new PercentageDiscount(new Percent(parameters[i])) : new FixedDiscount(new Money(parameters[i])), exclusive[i] ? StackingPolicy.EXCLUSIVE : StackingPolicy.STACKABLE, excluded));
            }
            boolean[] admissible = admissibleMasks(n, edges, exclusive);
            int selectedMask = 0;
            long remaining = original, budget = cap;
            for (int i = 0; i < n; i++) {
                if (!eligible[i] || !admissible[selectedMask | (1 << i)])
                    continue;
                long applied = Math.min(nominal(remaining, percent[i], parameters[i]), Math.min(remaining, budget));
                if (applied > 0) {
                    selectedMask |= 1 << i;
                    remaining -= applied;
                    budget -= applied;
                }
            }
            int bestMask = 0;
            long bestBenefit = 0;
            for (int i = 0; i < n; i++) if (eligible[i]) {
                long benefit = Math.min(nominal(original, percent[i], parameters[i]), Math.min(original, cap));
                if (benefit > bestBenefit) {
                    bestBenefit = benefit;
                    bestMask = 1 << i;
                }
            }
            var context = new PricingContext(new Basket(List.of(new CartLine("A", "books", new Money(original), 1))),
                    CustomerTier.STANDARD, Set.of());
            var priority = engine(promotions, cap, new PrioritySelection()).quote(context);
            var single = engine(promotions, cap, new BestSingleSelection()).quote(context);
            assertEquals(selectedMask, appliedMask(priority), "Graf " + scenario);
            assertEquals(new Money(remaining), priority.payable(), "Graf " + scenario);
            assertEquals(bestMask, appliedMask(single), "Graf " + scenario);
            assertEquals(new Money(bestBenefit), single.discount());
            assertTrue(admissible[appliedMask(priority)]);
            assertEquals(new Money(original), priority.payable().plus(priority.discount()));
            assertTrue(priority.discount().cents() <= cap);
            Collections.shuffle(promotions, random);
            assertEquals(priority, engine(promotions, cap, new PrioritySelection()).quote(context));
            assertEquals(single, engine(promotions, cap, new BestSingleSelection()).quote(context));
        }
    }

    private static boolean[] admissibleMasks(int n, boolean[][] edges, boolean[] exclusive) {
        boolean[] result = new boolean[1 << n];
        for (int mask = 0; mask < result.length; mask++) {
            boolean valid = true;
            for (int i = 0; i < n; i++) if ((mask & (1 << i)) != 0) {
                if (exclusive[i] && Integer.bitCount(mask) > 1)
                    valid = false;
                for (int j = i + 1; j < n; j++) {
                    if ((mask & (1 << j)) != 0 && (edges[i][j] || edges[j][i]))
                        valid = false;
                }
            }
            result[mask] = valid;
        }
        return result;
    }

    @Test
    void exhaustiveSmallAllocationsMatchMinimumSquaredErrorOracle() {
        int cases = 0;
        var allocator = new DiscountAllocator();
        for (int a = 0; a <= 4; a++) for (int b = 0; b <= 4; b++) for (int c = 0; c <= 4; c++) {
            int total = a + b + c;
            for (int amount = 0; amount <= total; amount++) {
                Map<String, Money> weights = Map.of("A", new Money(a), "B", new Money(b), "C", new Money(c));
                Map<String, Money> actual = allocator.allocate(new Money(amount), weights);
                assertEquals(allocationOracle(a, b, c, amount), actual, weights + " rabat=" + amount);
                assertEquals(amount, actual.values().stream().mapToLong(Money::cents).sum());
                actual.forEach((sku, value) -> assertTrue(value.compareTo(weights.get(sku)) <= 0));
                cases++;
            }
        }
        // Wyczerpujące tylko dla 3 wag 0..4 gr, nie dla całej dziedziny long.
        assertEquals(875, cases);
    }

    private static Map<String, Money> allocationOracle(int a, int b, int c, int amount) {
        int total = a + b + c;
        long bestScore = Long.MAX_VALUE;
        int bestA = -1, bestB = -1, bestC = -1;
        for (int x = 0; x <= a; x++) for (int y = 0; y <= b; y++) for (int z = 0; z <= c; z++) {
            if (x + y + z != amount)
                continue;
            long da = x * total - amount * a, db = y * total - amount * b, dc = z * total - amount * c;
            long score = da * da + db * db + dc * dc;
            // Mnożenie przez total usuwa dzielenie udziałów idealnych. Przy
            // równym błędzie preferujemy więcej groszy dla niższego SKU A, potem B.
            if (score < bestScore || score == bestScore && (x > bestA || x == bestA && y > bestB)) {
                bestScore = score;
                bestA = x;
                bestB = y;
                bestC = z;
            }
        }
        var result = new TreeMap<String, Money>();
        if (bestA > 0)
            result.put("A", new Money(bestA));
        if (bestB > 0)
            result.put("B", new Money(bestB));
        if (bestC > 0)
            result.put("C", new Money(bestC));
        return result;
    }

    private static long nominal(long base, boolean percent, int parameter) {
        return percent ? BigDecimal.valueOf(base).multiply(BigDecimal.valueOf(parameter))
                .divide(BigDecimal.valueOf(10000), 0, RoundingMode.HALF_UP).longValueExact() : parameter;
    }

    private static int appliedMask(PricingResult result) {
        int mask = 0;
        for (PromotionDecision decision : result.decisions()) {
            if (decision.status() == DecisionStatus.APPLIED)
                mask |= 1 << Integer.parseInt(decision.id().value().substring(1));
        }
        return mask;
    }

    private static PromotionEngine engine(List<Promotion> promotions, long cap, SelectionPolicy policy) {
        return new PromotionEngine(promotions, new Money(cap), CLOCK, policy);
    }
}
