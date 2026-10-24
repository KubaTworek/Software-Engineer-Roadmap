package pl.jakubtworek.lld.vendingmachine;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.vendingmachine.VendingException.Reason.*;

/**
 * Niezależny model z tablicami i enumeracją reszty sprawdza 2400 poleceń.
 * Nie korzysta z prywatnej sesji ani algorytmu DP. Dopuszcza dowolną poprawną
 * kombinację reszty: tutaj badamy wspólny kontrakt sprzedaży, a minimum
 * dla dodatkowej polityki sprawdza niezależna wyrocznia w ChangePolicyTest.
 * Losowanie ma stałe ziarna; test próbkowany nie jest pełnym dowodem wszystkich historii.
 */
class GeneratedCommandsTest {

    private static final Coin[] COINS = { Coin.GR20, Coin.GR50, Coin.PLN1 };
    private static final int[] PRICES = { 60, 100 };
    private static final SlotId[] SLOTS = { new SlotId("A1"), new SlotId("B1"), new SlotId("Z9") };

    private static final class Model {

        final int[] quantities = { 4, 3 };
        final int[] cash = { 2, 1, 1 };
        final int[] escrow = { 0, 0, 0 };
        int selected = -1;
        long revenue;
        long loaded;
        SaleReceipt lastSale;
    }

    // Tablice są kopiowane przy zapisie archiwum i potem niemodyfikowane.
    // Sam record nie zapewniłby niemutowalności tablic oczekiwań.
    private record Archive(MachineSnapshot view, int[] quantities, int[] cash, int[] escrow, int selected, long revenue,
            SaleReceipt lastSale) {
    }

    static Stream<Arguments> histories() {
        return Stream.of(17L, 71L, 213L, 901L).flatMap(seed -> Stream.of(Arguments.of(seed, new ExactChangePlanner()),
                Arguments.of(seed, new MinimumCoinChangePlanner())));
    }

    @ParameterizedTest
    @MethodSource("histories")
    void generatedCommandsPreserveMoneyStockAndHistoricalViews(long seed, ChangePolicy policy) {
        var machine = new VendingMachine(List.of(new StockItem(SLOTS[0], new Product("A", new Money(PRICES[0])), 4),
                new StockItem(SLOTS[1], new Product("B", new Money(PRICES[1])), 3)), CoinBundle.of(Coin.GR20, Coin.GR20,
                Coin.GR50, Coin.PLN1), policy);
        var model = new Model();
        var random = new Random(seed);
        var archives = new ArrayList<Archive>();
        for (int step = 0; step < 300; step++) {
            int slot = random.nextInt(3);
            int coin = random.nextInt(3);
            switch(random.nextInt(6)) {
                case 0 ->
                    {
                        var reason = model.selected >= 0 ? PURCHASE_IN_PROGRESS : slot == 2 ? UNKNOWN_SLOT : model.quantities[slot] == 0 ? OUT_OF_STOCK : null;
                        perform(machine, reason, () -> {
                            machine.select(SLOTS[slot]);
                            model.selected = slot;
                        });
                    }
                case 1 ->
                    {
                        var reason = model.selected < 0 ? NO_ACTIVE_PURCHASE : value(model.escrow) + COINS[coin]
                                .grosz() > 10000 ? CREDIT_LIMIT_EXCEEDED : null;
                        perform(machine, reason, () -> {
                            machine.insert(COINS[coin]);
                            model.escrow[coin]++;
                        });
                    }
                case 2 ->
                    buy(machine, model);
                case 3 ->
                    perform(machine, model.selected < 0 ? NO_ACTIVE_PURCHASE : null, () -> {
                        assertWallet(model.escrow, machine.cancel());
                        Arrays.fill(model.escrow, 0);
                        model.selected = -1;
                    });
                case 4 ->
                    perform(machine, model.selected >= 0 ? PURCHASE_IN_PROGRESS : slot == 2 ? UNKNOWN_SLOT : null,
                            () -> {
                        machine.restock(SLOTS[slot], 1);
                        model.quantities[slot]++;
                    });
                case 5 ->
                    perform(machine, model.selected >= 0 ? PURCHASE_IN_PROGRESS : null, () -> {
                        machine.loadCash(CoinBundle.of(COINS[coin]));
                        model.cash[coin]++;
                        model.loaded += COINS[coin].grosz();
                    });
                default ->
                    throw new AssertionError("Nieznana komenda generatora");
            }
            assertView(machine.snapshot(), model.quantities, model.cash, model.escrow, model.selected, model.revenue,
                    model.lastSale);
            // Kaseta rośnie tylko o dopłatę serwisową i ceny udanych zakupów.
            // Depozyt nie jest przychodem i pozostaje w oddzielnym zasobie.
            assertEquals(190 + model.loaded + model.revenue, machine.snapshot().cash().total().grosz());
            archives.add(new Archive(machine.snapshot(), model.quantities.clone(), model.cash.clone(),
                    model.escrow.clone(), model.selected, model.revenue, model.lastSale));
        }
        for (var old : archives) {
            assertView(old.view(), old.quantities(), old.cash(), old.escrow(), old.selected(), old.revenue(),
                    old.lastSale());
        }
    }

    private static void buy(VendingMachine machine, Model model) {
        int[] available = new int[3];
        for (int index = 0; index < 3; index++) {
            available[index] = model.cash[index] + model.escrow[index];
        }
        var reason = model.selected < 0 ? NO_ACTIVE_PURCHASE : value(model.escrow) < PRICES[model.selected] ? INSUFFICIENT_FUNDS : !canMake(value(model.escrow) - PRICES[model.selected], available, 0) ? NO_EXACT_CHANGE : null;
        perform(machine, reason, () -> {
            var receipt = machine.buy();
            assertEquals(SLOTS[model.selected], receipt.slotId());
            assertEquals(new Money(PRICES[model.selected]), receipt.product().price());
            assertWallet(model.escrow, receipt.inserted());
            assertEquals(value(model.escrow) - PRICES[model.selected], receipt.change().total().grosz());
            for (Coin coin : Coin.values()) {
                assertTrue(receipt.change().count(coin) <= count(available, coin));
            }
            // Wiele planów może być poprawnych. Po niezależnym sprawdzeniu
            // wartości i dostępności uznajemy zwrócony wybór za dozwolony,
            // zamiast zmuszać test do odtwarzania preferencji algorytmu DP.
            for (int index = 0; index < 3; index++) {
                model.cash[index] = available[index] - receipt.change().count(COINS[index]);
            }
            model.quantities[model.selected]--;
            model.revenue += PRICES[model.selected];
            model.selected = -1;
            Arrays.fill(model.escrow, 0);
            model.lastSale = receipt;
        });
    }

    private static boolean canMake(long amount, int[] available, int index) {
        if (index == COINS.length) {
            return amount == 0;
        }
        // Niezależna wyrocznia enumeruje mały zasób trzech nominałów. Jest
        // wolniejsza od DP i celowo ograniczona do danych tego generatora.
        long maximum = Math.min(available[index], amount / COINS[index].grosz());
        for (int used = 0; used <= maximum; used++) {
            if (canMake(amount - (long) used * COINS[index].grosz(), available, index + 1)) {
                return true;
            }
        }
        return false;
    }

    private static void perform(VendingMachine machine, VendingException.Reason reason, Runnable command) {
        if (reason == null) {
            command.run();
            return;
        }
        var before = machine.snapshot();
        assertEquals(reason, assertThrows(VendingException.class, command::run).reason());
        assertSame(before, machine.snapshot());
    }

    private static long value(int[] coins) {
        long value = 0;
        for (int index = 0; index < 3; index++) {
            value += (long) coins[index] * COINS[index].grosz();
        }
        return value;
    }

    private static int count(int[] counts, Coin coin) {
        for (int index = 0; index < 3; index++) {
            if (COINS[index] == coin) {
                return counts[index];
            }
        }
        return 0;
    }

    private static void assertWallet(int[] expected, CoinBundle actual) {
        for (Coin coin : Coin.values()) {
            assertEquals(count(expected, coin), actual.count(coin));
        }
        assertEquals(value(expected), actual.total().grosz());
    }

    private static void assertView(MachineSnapshot actual, int[] quantities, int[] cash, int[] escrow, int selected,
            long revenue, SaleReceipt lastSale) {
        assertEquals(2, actual.stock().size());
        for (int index = 0; index < 2; index++) {
            assertEquals(quantities[index], actual.stock().get(SLOTS[index]).quantity());
            assertEquals(new Money(PRICES[index]), actual.stock().get(SLOTS[index]).product().price());
        }
        assertWallet(cash, actual.cash());
        assertWallet(escrow, actual.escrow());
        assertEquals(new Money(revenue), actual.revenue());
        assertEquals(selected < 0 ? java.util.Optional.empty() : java.util.Optional.of(SLOTS[selected]),
                actual.selectedSlot());
        assertEquals(selected < 0 ? MachinePhase.IDLE : value(escrow) >= PRICES[selected] ? MachinePhase.FUNDED : MachinePhase.COLLECTING, actual.phase());
        assertSame(lastSale, actual.lastSale().orElse(null));
    }
}
