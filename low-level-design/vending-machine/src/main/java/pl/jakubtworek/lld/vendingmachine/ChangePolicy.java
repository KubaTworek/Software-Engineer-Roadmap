package pl.jakubtworek.lld.vendingmachine;

import java.util.Optional;

/**
 * Strategy opisuje rzeczywistą wymienność: znalezienie dowolnej dokładnej reszty
 * albo dodatkowo minimalizację liczby monet. Obie strategie muszą być kompletne:
 * empty oznacza brak rozwiązania, a nie rezygnację po nieudanej próbie greedy.
 * Wynik ma dokładną wartość amount i nie przekracza liczby dostępnych monet.
 * Zero wymaga obecnego, pustego zestawu; brak rozwiązania to Optional.empty().
 *
 * Implementacje nie modyfikują zasobów i działają bez I/O. Wspólny zakres wynosi
 * 0..10000 gr; null jest błędem programisty, przekroczenie limitu błędem argumentu.
 * Ten limit edukacyjny ogranicza tablice DP, nie opisuje własności waluty PLN.
 * Polityka wybiera monety, a automat nadal odpowiada za publikację zakupu.
 */
public interface ChangePolicy {

    int MAX_AMOUNT_GROSZ = 10_000;

    Optional<CoinBundle> plan(Money amount, CoinBundle available);
}
