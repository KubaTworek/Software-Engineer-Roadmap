package pl.jakubtworek.lld.logging.sink;

import pl.jakubtworek.lld.logging.domain.LogSink;
import java.util.*;

/**
 * Ograniczony jednowątkowy bufor rekordów. Domyślnie 1000 i DROP_OLDEST.
 * REJECT_NEW nie zmienia rekordów, ale zwiększa rejected i zgłasza IOException.
 * DROP_OLDEST to przyjęty zapis: logger zwróci WRITTEN, utratę historii pokazuje
 * dropped. Migawki records() są kopiami, a nie żywymi widokami kolejki.
 */
public final class MemoryLogSink implements LogSink {

    private final int capacity;
    private final OverflowPolicy policy;

    // Pierścień o stałej pojemności: brak powiększania tablicy i przesuwania rekordów.
    // head wskazuje najstarszy rekord, size rozróżnia pusty i pełny bufor.
    private final String[] records;
    private int head, size;
    private long rejected, dropped;

    public MemoryLogSink() {
        this(1000, OverflowPolicy.DROP_OLDEST);
    }

    public MemoryLogSink(int capacity, OverflowPolicy policy) {
        if (capacity < 1)
            throw new IllegalArgumentException("Pojemność musi być dodatnia");
        this.capacity = capacity;
        this.policy = Objects.requireNonNull(policy);
        records = new String[capacity];
    }

    public void write(String formatted) throws SinkCapacityException {
        Objects.requireNonNull(formatted);
        if (size == capacity) {
            if (policy == OverflowPolicy.REJECT_NEW) {
                rejected = increment(rejected);
                throw new SinkCapacityException(capacity);
            }
            records[head] = null;
            head = head == capacity - 1 ? 0 : head + 1;
            size--;
            dropped = increment(dropped);
        }
        // Dodawanie wykonujemy w long przed modulo; suma dwóch poprawnych
        // indeksów int mogłaby się przepełnić jeszcze przed zawinięciem pierścienia.
        records[(int) (((long) head + size) % capacity)] = formatted;
        size++;
    }

    public List<String> records() {
        var result = new ArrayList<String>(size);
        for (int i = 0; i < size; i++) {
            result.add(records[(int) (((long) head + i) % capacity)]);
        }
        return List.copyOf(result);
    }

    public MemorySinkDiagnostics diagnostics() {
        return new MemorySinkDiagnostics(capacity, policy, size, rejected, dropped);
    }

    public void clear() {
        // Samo size = 0 pozostawiłoby stare teksty osiągalne przez tablicę.
        // Usuwamy referencje bufora; zewnętrzne snapshoty nadal mogą je utrzymywać.
        Arrays.fill(records, null);
        head = size = 0;
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? value : value + 1;
    }
}
