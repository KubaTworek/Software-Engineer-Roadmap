package pl.jakubtworek.marketplace.integration.cdc;

import java.util.*;

/**
 * Deterministyczny model granicy Debezium: initial snapshot, przejście do logu,
 * checkpoint per partycja, deduplikacja, poison record i bezpieczny backfill.
 * Offset jest porządkiem technicznym tylko wewnątrz partycji; nie udajemy porządku
 * globalnego. Projektor aktualizuje wyłącznie read model, więc replay nie powtarza
 * płatności, maili ani innych efektów biznesowych.
 */
public final class CdcProjectionPipeline<K, V> {
    public record Position(int partition, long offset) implements Comparable<Position> {
        @Override public int compareTo(Position other) {
            int byPartition = Integer.compare(partition, other.partition);
            return byPartition != 0 ? byPartition : Long.compare(offset, other.offset);
        }
    }
    public record Change<K, V>(Position position, K key, V value, boolean deleted) {}
    @FunctionalInterface public interface Projector<K, V> {
        void apply(Map<K, V> projection, Change<K, V> change);
    }

    private final Map<K, V> projection = new HashMap<>();
    private final Map<Integer, Long> checkpoints = new HashMap<>();
    private final List<Change<K, V>> buffered = new ArrayList<>();
    private final Map<Integer, Change<K, V>> poison = new HashMap<>();
    private final Projector<K, V> projector;
    private boolean snapshotFinished;

    public CdcProjectionPipeline(Projector<K, V> projector) { this.projector = projector; }

    public void onChange(Change<K, V> change) {
        if (!snapshotFinished || poison.containsKey(change.position().partition())) {
            buffered.add(change);
            return;
        }
        applyIfNew(change);
    }

    /** Snapshot i watermarks muszą pochodzić z jednego spójnego punktu źródła. */
    public void finishInitialSnapshot(Map<K, V> snapshot, Map<Integer, Long> watermarks) {
        if (snapshotFinished) throw new IllegalStateException("initial snapshot already completed");
        projection.clear();
        projection.putAll(snapshot);
        checkpoints.putAll(watermarks);
        snapshotFinished = true;
        List<Change<K, V>> waiting = new ArrayList<>(buffered);
        buffered.clear();
        waiting.stream().sorted(Comparator.comparing(Change::position)).forEach(this::applyIfNew);
    }

    private void applyIfNew(Change<K, V> change) {
        int partition = change.position().partition();
        if (change.position().offset() <= checkpoints.getOrDefault(partition, -1L)) return;
        if (poison.containsKey(partition)) { buffered.add(change); return; }
        try {
            projector.apply(projection, change);
            checkpoints.put(partition, change.position().offset());
        } catch (RuntimeException invalidRecord) {
            poison.put(partition, change);
        }
    }

    /** Po wdrożeniu poprawki ponawia rekord i odblokowuje wyłącznie jego partycję. */
    public void retryPoison(int partition) {
        Change<K, V> change = poison.remove(partition);
        if (change == null) return;
        applyIfNew(change);
        if (!poison.containsKey(partition)) {
            List<Change<K, V>> samePartition = buffered.stream()
                    .filter(it -> it.position().partition() == partition).sorted(Comparator.comparing(Change::position)).toList();
            buffered.removeAll(samePartition);
            samePartition.forEach(this::applyIfNew);
        }
    }

    /** Backfill naprawia projekcję wprost; celowo nie publikuje domain events. */
    public void backfill(Map<K, V> authoritativeRows) { projection.putAll(authoritativeRows); }
    public Map<K, V> projection() { return Map.copyOf(projection); }
    public Map<Integer, Long> checkpoints() { return Map.copyOf(checkpoints); }
    public Optional<Change<K, V>> poison(int partition) { return Optional.ofNullable(poison.get(partition)); }

    public static <K, V> Projector<K, V> upsertProjector() {
        return (projection, change) -> {
            if (change.deleted()) projection.remove(change.key()); else projection.put(change.key(), change.value());
        };
    }
}
