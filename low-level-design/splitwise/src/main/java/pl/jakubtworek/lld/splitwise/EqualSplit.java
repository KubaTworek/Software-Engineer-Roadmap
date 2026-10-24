package pl.jakubtworek.lld.splitwise;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Iloraz daje kwotę podstawową, reszta mówi ilu osobom dopisać po groszu.
 * Dla 100 gr / 3 powstaje 34,33,33, a nie 33,33,33. Kolejność wejściowej
 * listy nie zmienia wyniku: reszty dostają najmniejsze leksykograficznie ID.
 * Stały remis nie oznacza sprawiedliwości naprzemiennej w wielu wydatkach.
 */
public final class EqualSplit implements SplitPolicy {

    private final List<MemberId> participants;

    public EqualSplit(List<MemberId> participants) {
        var copy = List.copyOf(participants);
        if (copy.isEmpty() || new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException("Uczestnicy muszą być niepuści i unikalni");
        }
        this.participants = copy.stream().sorted(Comparator.comparing(MemberId::value)).toList();
    }

    @Override
    public Map<MemberId, Money> split(Money total) {
        Objects.requireNonNull(total, "total");
        long base = total.grosz() / participants.size();
        long remainder = total.grosz() % participants.size();
        var result = new HashMap<MemberId, Money>();
        for (int index = 0; index < participants.size(); index++) {
            result.put(participants.get(index), new Money(base + (index < remainder ? 1 : 0)));
        }
        return Map.copyOf(result);
    }
}
