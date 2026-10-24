package pl.jakubtworek.lld.atm;

import java.util.Map;

/**
 * Konkretny niepusty zestaw wydawanych banknotów, nie mutowalna rezerwacja kasety.
 * Dopuszcza tylko dodatnie liczności, dzięki czemu wynik nie zawiera pozornych
 * nominałów z zerem banknotów. Sumy liczymy w long przed dodawaniem.
 */
public record CashBundle(Map<Banknote, Integer> notes) {

    public CashBundle {
        notes = Map.copyOf(notes);
        if (notes.isEmpty() || notes.values().stream().anyMatch(count -> count <= 0)) {
            throw new IllegalArgumentException("Zestaw wymaga dodatnich liczności banknotów");
        }
    }

    public Money total() {
        long sum = 0;
        for (var item : notes.entrySet()) {
            sum = Math.addExact(sum, Math.multiplyExact((long) item.getKey().grosz(), item.getValue()));
        }
        return new Money(sum);
    }

    public long count() {
        return notes.values().stream().mapToLong(Integer::longValue).sum();
    }
}
