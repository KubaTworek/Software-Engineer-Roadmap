package pl.jakubtworek.lld.change;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Jeden techniczny rdzeń dla monet automatu i banknotów bankomatu.
 * Wejście to nominały i liczności w prymitywnych tablicach; adapter wybiera
 * jednostkę kwoty (grosz albo 10 PLN) i tłumaczy wynik na własne enumy.
 * Nie ma tu kont, sesji, produktów ani waluty. Kolejność nominałów nie musi
 * być posortowana; wynik odpowiada dokładnie pozycjom tablic wejściowych.
 */
public final class BoundedChangePlanner {

    private final int maximumAmount;

    public BoundedChangePlanner(int maximumAmount) {
        if (maximumAmount < 0 || maximumAmount == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Limit musi pozwalać na tablicę amount+1");
        }
        this.maximumAmount = maximumAmount;
    }

    /**
     * Minimum liczby sztuk w O(D*A), pamięć rekonstrukcji O(D*A).
     * Przy remisie zachowujemy najwcześniejszego poprzednika. Jest to reguła
     * deterministyczna, lecz nie optymalizacja zasobu na przyszłe wypłaty.
     * Nie przechowujemy referencji do tablic klienta i nie zmieniamy ich treści.
     */
    public Optional<int[]> plan(int amount, int[] denominations, int[] available) {
        Objects.requireNonNull(denominations);
        Objects.requireNonNull(available);
        if (amount < 0 || amount > maximumAmount || denominations.length != available.length) {
            throw new IllegalArgumentException("Niepoprawna kwota albo rozmiary tablic");
        }
        for (int i = 0; i < denominations.length; i++) {
            if (denominations[i] <= 0 || available[i] < 0) {
                throw new IllegalArgumentException("Nominał musi być dodatni, liczność nieujemna");
            }
        }
        // Dodatnie całkowite nominały wymagają najwyżej amount sztuk.
        // amount + 1 oznacza nieosiągalność; domyślne zero tablicy oznaczałoby sukces.
        int unreachable = amount + 1;
        int[] best = new int[amount + 1], next = new int[amount + 1], deque = new int[amount + 1];
        Arrays.fill(best, unreachable);
        best[0] = 0;
        // int[][] jest tablicą referencji do wierszy. Alokujemy tylko warstwy,
        // które rzeczywiście wykorzystują dany nominał; DP nie boksuje indeksów.
        int[][] taken = new int[denominations.length][];
        for (int layer = 0; layer < denominations.length; layer++) {
            int value = denominations[layer];
            int limit = Math.min(available[layer], amount / value);
            if (limit == 0) {
                continue;
            }
            taken[layer] = new int[amount + 1];
            Arrays.fill(next, unreachable);
            // Dla kwoty r+t*w: next=t+min(best[r+j*w]-j), j w [t-limit,t].
            // Kolejka trzyma rosnące koszty, a jej początek jest minimum okna.
            // Każdy indeks wchodzi i wychodzi najwyżej raz. Nie ma pętli po
            // liczbie posiadanych monet/banknotów, nawet gdy wynosi MAX_VALUE.
            for (int residue = 0; residue < value && residue <= amount; residue++) {
                int head = 0, tail = 0;
                for (int t = 0, target = residue; target <= amount; t++, target += value) {
                    while (head < tail && deque[head] < t - limit) {
                        head++;
                    }
                    if (best[target] != unreachable) {
                        int score = best[target] - t;
                        while (head < tail && best[residue + deque[tail - 1] * value] - deque[tail - 1] > score) {
                            tail--;
                        }
                        deque[tail++] = t;
                    }
                    if (head < tail) {
                        int source = deque[head];
                        next[target] = best[residue + source * value] + t - source;
                        taken[layer][target] = t - source;
                    }
                    // Unikamy przepełnienia target+value przy dużym nominale.
                    if (amount - target < value) {
                        break;
                    }
                }
            }
            // Zamieniamy referencje do dwóch osobnych buforów, bez kopiowania.
            // Aliasy best i next pozwoliłyby ponownie zużyć zapas bieżącej warstwy.
            int[] swap = best;
            best = next;
            next = swap;
        }
        if (best[amount] == unreachable) {
            return Optional.empty();
        }
        int[] result = new int[denominations.length];
        int remaining = amount;
        for (int i = result.length - 1; i >= 0; i--) {
            if (taken[i] == null) {
                continue;
            }
            result[i] = taken[i][remaining];
            remaining -= result[i] * denominations[i];
        }
        if (remaining != 0) {
            throw new IllegalStateException("Niespójna rekonstrukcja");
        }
        return Optional.of(result);
    }
}
