package pl.jakubtworek.backend_engineering.stage_2.block_b.cdc_reconciliation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compares full canonical values, not only row counts or timestamps. */
public final class ProjectionReconciler {

    public List<Drift> compare(List<AuthoritativeOrder> sourceRows, OrderProjectionStore projection) {
        Map<String, AuthoritativeOrder> source = indexSource(sourceRows);
        Map<String, OrderProjectionStore.ProjectedOrder> readModel = new HashMap<>();
        projection.all().forEach(row -> readModel.put(row.id(), row));
        List<Drift> drift = new ArrayList<>();

        for (AuthoritativeOrder expected : source.values()) {
            OrderProjectionStore.ProjectedOrder actual = readModel.remove(expected.id());
            if (actual == null) {
                drift.add(new Drift(expected.id(), DriftType.MISSING, expected, null));
            } else if (!actual.asAuthoritative().equals(expected)) {
                drift.add(new Drift(expected.id(), DriftType.VALUE_MISMATCH, expected, actual));
            }
        }
        readModel.values().forEach(orphan ->
                drift.add(new Drift(orphan.id(), DriftType.ORPHAN, null, orphan)));
        return drift.stream().sorted(java.util.Comparator.comparing(Drift::key)).toList();
    }

    public RepairReport repair(
            List<AuthoritativeOrder> sourceRows,
            OrderProjectionStore projection
    ) {
        List<Drift> before = compare(sourceRows, projection);
        Map<String, AuthoritativeOrder> source = indexSource(sourceRows);
        for (Drift difference : before) {
            if (difference.type() == DriftType.ORPHAN) {
                projection.removeOrphan(difference.key());
            } else {
                projection.repairFromSource(source.get(difference.key()));
            }
        }
        return new RepairReport(before, compare(sourceRows, projection));
    }

    private static Map<String, AuthoritativeOrder> indexSource(List<AuthoritativeOrder> rows) {
        Map<String, AuthoritativeOrder> indexed = new HashMap<>();
        for (AuthoritativeOrder row : rows) {
            if (indexed.put(row.id(), row) != null) {
                throw new IllegalArgumentException("duplicate source row " + row.id());
            }
        }
        return indexed;
    }

    public enum DriftType {
        MISSING,
        VALUE_MISMATCH,
        ORPHAN
    }

    public record Drift(
            String key,
            DriftType type,
            AuthoritativeOrder expected,
            OrderProjectionStore.ProjectedOrder actual
    ) {
    }

    public record RepairReport(List<Drift> detected, List<Drift> remaining) {
        public RepairReport {
            detected = List.copyOf(detected);
            remaining = List.copyOf(remaining);
        }
    }
}
