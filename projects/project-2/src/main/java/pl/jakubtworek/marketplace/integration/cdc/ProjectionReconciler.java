package pl.jakubtworek.marketplace.integration.cdc;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Model rekoncyliacji źródła prawdy z projekcją zasilaną przez CDC.
 *
 * Debezium/outbox przenosi zmiany, lecz nie dowodzi kompletności read modelu.
 * Snapshot, restart konektora, błędna kolejność albo poison record mogą pozostawić
 * trwały drift. Rekonsyliator porównuje stabilny identyfikator i oczekiwany stan.
 * Naprawa zapisuje projekcję bez ponownego wykonywania efektów biznesowych — nie
 * pobiera płatności i nie emituje ponownie OrderPlaced.
 */
public final class ProjectionReconciler<K, V> {
    public Report<K> reconcile(Map<K, V> source, Map<K, V> projection, BiConsumer<K, V> repair) {
        var missing = new ArrayList<K>();
        var divergent = new ArrayList<K>();
        source.forEach((key, expected) -> {
            if (!projection.containsKey(key)) missing.add(key);
            else if (!Objects.equals(expected, projection.get(key))) divergent.add(key);
        });
        missing.forEach(key -> repair.accept(key, source.get(key)));
        divergent.forEach(key -> repair.accept(key, source.get(key)));
        return new Report<>(java.util.List.copyOf(missing), java.util.List.copyOf(divergent));
    }

    public record Report<K>(java.util.List<K> missing, java.util.List<K> divergent) {
        public boolean consistent() { return missing.isEmpty() && divergent.isEmpty(); }
    }
}
