package pl.jakubtworek.lld.vendingmachine;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.vendingmachine.Coin.*;
import static pl.jakubtworek.lld.vendingmachine.Fixtures.*;
import static pl.jakubtworek.lld.vendingmachine.VendingException.Reason.*;

/**
 * Scenariusze publicznego API: legalne przejścia, odmowy i zachowanie zasobów.
 * Stan po odmowie ma być tym samym widokiem; sam wyjątek nie wystarcza do
 * wykazania, że klient zachował swoje monety i produkt nie został zabrany.
 */
class VendingMachineTest {

    @Test
    void strategiesDifferInCoinCountButPreserveTheSameSaleInvariants() {
        var cash = new CoinBundle(Map.of(GR50, 1, GR20, 3, GR10, 1));
        var items = List.of(new StockItem(SLOT, new Product("P", new Money(440)), 1));
        var any = new VendingMachine(items, cash);
        var minimum = new VendingMachine(items, cash, new MinimumCoinChangePlanner());
        for (var machine : List.of(any, minimum)) {
            machine.select(SLOT);
            machine.insert(PLN5);
            var receipt = machine.buy();
            assertEquals(new Money(60), receipt.change().total());
            assertEquals(cash.plus(CoinBundle.of(PLN5)).minus(receipt.change()), machine.snapshot().cash());
            assertEquals(new Money(440), machine.snapshot().revenue());
            assertEquals(0, machine.snapshot().stock().get(SLOT).quantity());
            assertEquals(MachinePhase.IDLE, machine.snapshot().phase());
        }
        // Różnica wynika z kryterium, nie z innych danych czy ceny. To przykład
        // wybranego planu domyślnego, nie obietnica kontraktu dla każdego remisu.
        assertEquals(new CoinBundle(Map.of(GR20, 3)), any.snapshot().lastSale().orElseThrow().change());
        assertEquals(CoinBundle.of(GR50, GR10), minimum.snapshot().lastSale().orElseThrow().change());
    }

    @Test
    void purchaseOverflowLeavesOriginalDepositRefundable() {
        for (ChangePolicy policy : List.of(new ExactChangePlanner(), new MinimumCoinChangePlanner())) {
            var cash = new CoinBundle(Map.of(GR1, Integer.MAX_VALUE));
            var machine = new VendingMachine(List.of(new StockItem(SLOT, new Product("P", new Money(1)), 1)), cash,
                    policy);
            machine.select(SLOT);
            machine.insert(GR1);
            var funded = machine.snapshot();
            // Cena jest pokryta, lecz kandydat kaseta+depozyt przekracza int.
            // To błąd reprezentacji liczności, nie brak dokładnej reszty.
            assertThrows(ArithmeticException.class, machine::buy);
            assertSame(funded, machine.snapshot());
            assertEquals(1, machine.snapshot().stock().get(SLOT).quantity());
            assertEquals(cash, machine.snapshot().cash());
            assertEquals(new Money(0), machine.snapshot().revenue());
            assertTrue(machine.snapshot().lastSale().isEmpty());
            assertEquals(CoinBundle.of(GR1), machine.cancel());
            assertEquals(MachinePhase.IDLE, machine.snapshot().phase());
            assertEquals(CoinBundle.of(GR1), funded.escrow());
            // Po anulowaniu nadal można kupić innym nominałem. Błąd jednego
            // polecenia nie zatruwa automatu; kaseta nie zwiększa liczby GR1.
            machine.select(SLOT);
            machine.insert(GR2);
            assertEquals(CoinBundle.of(GR1), machine.buy().change());
        }
    }

    @Test
    void invalidOrFailingPolicyCannotPublishPartialPurchase() {
        // Kolejne implementacje zwracają złą kwotę, niedostępne monety,
        // rzucają wyjątek lub łamią kontrakt Optional. Wspólny interfejs
        // nie gwarantuje poprawnego zachowania dostarczonej implementacji.
        List<ChangePolicy> policies = List.of((amount, cash) -> java.util.Optional.of(CoinBundle.empty()), (amount,
                cash) -> java.util.Optional.of(CoinBundle.of(GR50, GR50)), (amount, cash) -> {
            throw new IllegalStateException("Awaria strategii");
        }, (amount, cash) -> null);
        for (ChangePolicy policy : policies) {
            var machine = new VendingMachine(List.of(new StockItem(SLOT, new Product("P", new Money(100)), 1)),
                    CoinBundle.empty(), policy);
            machine.select(SLOT);
            machine.insert(PLN2);
            var before = machine.snapshot();
            // Tutaj każda polityka celowo łamie kontrakt. Typ wyjątku zależy
            // od naruszenia; wspólną badaną gwarancją jest brak publikacji.
            assertThrows(RuntimeException.class, machine::buy);
            assertSame(before, machine.snapshot());
            assertEquals(CoinBundle.of(PLN2), machine.cancel());
        }
        assertThrows(NullPointerException.class, () -> new VendingMachine(List.of(new StockItem(SLOT, new Product("P",
                new Money(100)), 1)), CoinBundle.empty(), null));
    }

    @Test
    void exactPaymentCommitsProductMoneyAndReceiptTogether() {
        var machine = machine(250, 2, CoinBundle.empty());
        var empty = machine.snapshot();
        assertEquals(MachinePhase.IDLE, empty.phase());
        machine.select(SLOT);
        assertEquals(MachinePhase.COLLECTING, machine.snapshot().phase());
        machine.insert(PLN2);
        var partial = machine.snapshot();
        assertEquals(new Money(200), partial.credit());
        assertEquals(CoinBundle.empty(), partial.cash());
        machine.insert(GR50);
        assertEquals(MachinePhase.FUNDED, machine.snapshot().phase());
        var funded = machine.snapshot();
        var receipt = machine.buy();
        assertEquals(CoinBundle.empty(), receipt.change());
        assertEquals(CoinBundle.of(PLN2, GR50), receipt.inserted());
        assertEquals(1, machine.snapshot().stock().get(SLOT).quantity());
        assertEquals(new Money(250), machine.snapshot().cash().total());
        assertEquals(new Money(250), machine.snapshot().revenue());
        assertEquals(MachinePhase.IDLE, machine.snapshot().phase());
        assertEquals(new Money(0), machine.snapshot().credit());
        assertSame(receipt, machine.snapshot().lastSale().orElseThrow());
        assertEquals(2, empty.stock().get(SLOT).quantity());
        assertEquals(MachinePhase.COLLECTING, partial.phase());
        assertEquals(MachinePhase.FUNDED, funded.phase());
        assertEquals(CoinBundle.empty(), funded.cash());
    }

    @Test
    void insertedCoinsMayContributeToChangeOnlyDuringSuccessfulCommit() {
        var machine = machine(100, 1, CoinBundle.empty());
        machine.select(SLOT);
        machine.insert(PLN1);
        machine.insert(GR50);
        var result = machine.buy();
        assertEquals(CoinBundle.of(GR50), result.change());
        assertEquals(CoinBundle.of(PLN1), machine.snapshot().cash());
        assertEquals(new Money(100), machine.snapshot().revenue());
    }

    @Test
    void unavailableChangePreservesEverythingAndCancellationReturnsOriginalCoin() {
        var machine = machine(250, 2, CoinBundle.empty());
        machine.select(SLOT);
        machine.insert(PLN5);
        var funded = machine.snapshot();
        reject(machine, NO_EXACT_CHANGE, machine::buy);
        assertEquals(MachinePhase.FUNDED, machine.snapshot().phase());
        var refund = machine.cancel();
        assertEquals(CoinBundle.of(PLN5), refund);
        assertEquals(2, machine.snapshot().stock().get(SLOT).quantity());
        assertEquals(new Money(0), machine.snapshot().revenue());
        assertEquals(CoinBundle.empty(), machine.snapshot().cash());
        assertEquals(CoinBundle.of(PLN5), funded.escrow());
        assertEquals(MachinePhase.IDLE, machine.snapshot().phase());
    }

    @Test
    void insufficientCreditCanBeToppedUpAndThenPurchased() {
        var machine = machine(100, 1, CoinBundle.empty());
        machine.select(SLOT);
        machine.insert(GR50);
        reject(machine, INSUFFICIENT_FUNDS, machine::buy);
        machine.insert(GR50);
        assertEquals(new Money(100), machine.buy().product().price());
    }

    @Test
    void additionalCoinsAfterChangeFailureStayRefundableEvenIfChangeStillImpossible() {
        var machine = machine(100, 1, CoinBundle.empty());
        machine.select(SLOT);
        machine.insert(PLN2);
        reject(machine, NO_EXACT_CHANGE, machine::buy);
        // Dopłata tworzy teraz możliwość reszty: 250-100=150, ale brak PLN1
        // nadal ją blokuje. Anulowanie pozostaje bezpieczne po wielu odmowach.
        machine.insert(GR50);
        reject(machine, NO_EXACT_CHANGE, machine::buy);
        assertEquals(CoinBundle.of(PLN2, GR50), machine.cancel());
    }

    @Test
    void additionalMatchingCoinCanResolveEarlierChangeFailure() {
        var machine = machine(100, 1, CoinBundle.empty());
        machine.select(SLOT);
        machine.insert(PLN2);
        reject(machine, NO_EXACT_CHANGE, machine::buy);
        machine.insert(PLN1);
        // Nowa wpłata umożliwia oddanie starej monety PLN2 jako reszty.
        // Sama wartość kredytu wcześniej wystarczała, ale brakowało nominałów.
        assertEquals(CoinBundle.of(PLN2), machine.buy().change());
        assertEquals(CoinBundle.of(PLN1), machine.snapshot().cash());
    }

    @Test
    void greedyTrapSucceedsWithThreeTwentyGroszCoins() {
        var machine = machine(440, 1, new CoinBundle(Map.of(GR50, 1, GR20, 3)));
        machine.select(SLOT);
        machine.insert(PLN5);
        var result = machine.buy();
        assertEquals(new CoinBundle(Map.of(GR20, 3)), result.change());
        assertEquals(CoinBundle.of(GR50, PLN5), machine.snapshot().cash());
        assertEquals(new Money(440), machine.snapshot().revenue());
    }

    @Test
    void illegalCommandsDoNotChangePhaseOrResources() {
        var machine = machine(100, 1, CoinBundle.of(PLN1));
        reject(machine, NO_ACTIVE_PURCHASE, () -> machine.insert(PLN1));
        reject(machine, NO_ACTIVE_PURCHASE, machine::buy);
        reject(machine, NO_ACTIVE_PURCHASE, machine::cancel);
        reject(machine, UNKNOWN_SLOT, () -> machine.select(new SlotId("Z9")));
        machine.select(SLOT);
        reject(machine, PURCHASE_IN_PROGRESS, () -> machine.select(new SlotId("Z9")));
        reject(machine, PURCHASE_IN_PROGRESS, () -> machine.restock(SLOT, 1));
        reject(machine, PURCHASE_IN_PROGRESS, () -> machine.loadCash(CoinBundle.of(PLN1)));
        assertEquals(CoinBundle.empty(), machine.cancel());
    }

    @Test
    void soldOutProductRequiresRestockingBeforeSelection() {
        var machine = machine(100, 0, CoinBundle.empty());
        reject(machine, OUT_OF_STOCK, () -> machine.select(SLOT));
        machine.restock(SLOT, 2);
        machine.select(SLOT);
        machine.insert(PLN1);
        machine.buy();
        assertEquals(1, machine.snapshot().stock().get(SLOT).quantity());
    }

    @Test
    void maximumCreditIsAcceptedAndExcessCoinIsNotSwallowed() {
        var machine = machine(10000, 1, CoinBundle.empty());
        machine.select(SLOT);
        for (int count = 0; count < 20; count++) {
            machine.insert(PLN5);
        }
        reject(machine, CREDIT_LIMIT_EXCEEDED, () -> machine.insert(GR1));
        assertEquals(new Money(10000), machine.snapshot().credit());
        assertEquals(CoinBundle.empty(), machine.buy().change());
        assertEquals(new Money(10000), machine.snapshot().revenue());
    }

    @Test
    void serviceAddsStockAndCashButNeverSalesRevenue() {
        var machine = machine(250, 1, CoinBundle.empty());
        var before = machine.snapshot();
        machine.loadCash(CoinBundle.of(PLN2, GR50));
        machine.restock(SLOT, 2);
        assertEquals(3, machine.snapshot().stock().get(SLOT).quantity());
        assertEquals(new Money(250), machine.snapshot().cash().total());
        assertEquals(new Money(0), machine.snapshot().revenue());
        assertEquals(1, before.stock().get(SLOT).quantity());
        assertEquals(CoinBundle.empty(), before.cash());
    }

    @Test
    void serviceOverflowDoesNotPublishPartialState() {
        var machine = machine(1, Integer.MAX_VALUE, new CoinBundle(Map.of(GR1, Integer.MAX_VALUE)));
        var before = machine.snapshot();
        assertThrows(ArithmeticException.class, () -> machine.restock(SLOT, 1));
        assertThrows(ArithmeticException.class, () -> machine.loadCash(CoinBundle.of(GR1)));
        assertThrows(IllegalArgumentException.class, () -> machine.restock(SLOT, 0));
        assertSame(before, machine.snapshot());
    }

    @Test
    void immutableSourceCollectionsAndIndependentMachinesProtectOwnership() {
        var items = new ArrayList<>(List.of(new StockItem(SLOT, new Product("P", new Money(100)), 2)));
        var cash = CoinBundle.of(PLN1);
        var first = new VendingMachine(items, cash);
        var second = new VendingMachine(items, cash);
        items.clear();
        first.select(SLOT);
        first.insert(PLN1);
        first.buy();
        assertEquals(2, second.snapshot().stock().get(SLOT).quantity());
        assertEquals(cash, second.snapshot().cash());
        assertThrows(UnsupportedOperationException.class, () -> first.snapshot().stock().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.snapshot().cash().counts().clear());
    }

    @Test
    void nullInputsAreProgrammingErrorsAndLeaveStateUnchanged() {
        var machine = machine(100, 1, CoinBundle.empty());
        var before = machine.snapshot();
        assertThrows(NullPointerException.class, () -> machine.select(null));
        assertThrows(NullPointerException.class, () -> machine.insert(null));
        assertThrows(NullPointerException.class, () -> machine.loadCash(null));
        assertThrows(NullPointerException.class, () -> machine.restock(null, 1));
        assertSame(before, machine.snapshot());
    }

    @Test
    void lastReceiptSurvivesAnotherCancelledSessionWithoutBecomingHistoryLog() {
        var machine = machine(100, 2, CoinBundle.empty());
        machine.select(SLOT);
        machine.insert(PLN1);
        var first = machine.buy();
        machine.select(SLOT);
        machine.insert(PLN2);
        assertEquals(CoinBundle.of(PLN2), machine.cancel());
        assertSame(first, machine.snapshot().lastSale().orElseThrow());
        assertEquals(new Money(100), machine.snapshot().revenue());
    }

    private static void reject(VendingMachine machine, VendingException.Reason reason, Runnable command) {
        var before = machine.snapshot();
        var error = assertThrows(VendingException.class, command::run);
        assertEquals(reason, error.reason());
        assertSame(before, machine.snapshot(), "Odmowa nie może publikować częściowo zmienionego widoku");
    }
}
