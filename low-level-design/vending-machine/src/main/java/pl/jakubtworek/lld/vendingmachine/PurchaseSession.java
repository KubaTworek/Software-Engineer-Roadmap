package pl.jakubtworek.lld.vendingmachine;

import java.util.Objects;

/**
 * Zamknięty zbiór poprawnych postaci sesji. Idle nie ma pola z pieniędzmi,
 * więc nie da się skonstruować „brak zakupu, ale zagubiony depozyt”. Selected
 * zawsze ma kod i monety. sealed ogranicza dozwolone podtypy. Record daje
 * finalne referencje, lecz bezpieczeństwo współdzielenia depozytu wynika
 * również z niemutowalności CoinBundle, a nie z samego słowa record.
 */
sealed interface PurchaseSession {

    record Idle() implements PurchaseSession {
    }

    record Selected(SlotId slotId, CoinBundle inserted) implements PurchaseSession {

        public Selected {
            Objects.requireNonNull(slotId, "slotId");
            Objects.requireNonNull(inserted, "inserted");
        }
    }
}
