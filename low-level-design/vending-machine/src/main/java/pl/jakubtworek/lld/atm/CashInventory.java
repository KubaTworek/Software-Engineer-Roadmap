package pl.jakubtworek.lld.atm;

import java.util.EnumMap;
import java.util.Map;

/**
 * Niemutowalna kaseta. Brak nominału oznacza zero sztuk; zerowe wpisy wejścia
 * są usuwane. without najpierw weryfikuje cały zestaw, dopiero buduje następną
 * kasetę. Metoda nie publikuje jej niezależnie od konta i potwierdzenia.
 */
public final class CashInventory {

    private final Map<Banknote, Integer> notes;

    public CashInventory(Map<Banknote, Integer> notes) {
        var copy = new EnumMap<Banknote, Integer>(Banknote.class);
        Map.copyOf(notes).forEach((note, count) -> {
            if (count < 0) {
                throw new IllegalArgumentException("Liczność kasety nie może być ujemna");
            }
            if (count > 0) {
                copy.put(note, count);
            }
        });
        this.notes = Map.copyOf(copy);
    }

    public int count(Banknote note) {
        return notes.getOrDefault(java.util.Objects.requireNonNull(note, "note"), 0);
    }

    public Map<Banknote, Integer> notes() {
        return notes;
    }

    public Money total() {
        long sum = 0;
        for (var item : notes.entrySet()) {
            sum = Math.addExact(sum, Math.multiplyExact((long) item.getKey().grosz(), item.getValue()));
        }
        return new Money(sum);
    }

    public CashInventory without(CashBundle bundle) {
        bundle.notes().forEach((note, count) -> {
            if (count > count(note)) {
                throw new IllegalArgumentException("Plan przekracza zasób nominału");
            }
        });
        var next = new EnumMap<Banknote, Integer>(Banknote.class);
        next.putAll(notes);
        bundle.notes().forEach((note, count) -> next.put(note, count(note) - count));
        return new CashInventory(next);
    }
}
