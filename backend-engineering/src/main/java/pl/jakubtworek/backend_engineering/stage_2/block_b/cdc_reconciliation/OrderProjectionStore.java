package pl.jakubtworek.backend_engineering.stage_2.block_b.cdc_reconciliation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** A materialized view that uses source version to survive duplicate and reordered delivery. */
public final class OrderProjectionStore {

    private final Map<String, ProjectedOrder> rows = new HashMap<>();
    private final Map<String, Long> highestVersionByKey = new HashMap<>();
    private final Set<String> processedEventIds = new HashSet<>();

    public synchronized ApplyResult apply(CdcRecord record) {
        validate(record);
        if (!processedEventIds.add(record.eventId())) {
            return ApplyResult.DUPLICATE;
        }

        long currentVersion = highestVersionByKey.getOrDefault(record.key(), 0L);
        if (record.sourceVersion() <= currentVersion) {
            return ApplyResult.STALE;
        }

        boolean gap = record.origin() == CdcRecord.Origin.STREAM
                && record.sourceVersion() > currentVersion + 1;
        highestVersionByKey.put(record.key(), record.sourceVersion());
        if (record.operation() == CdcRecord.Operation.DELETE) {
            rows.remove(record.key());
        } else {
            AuthoritativeOrder after = record.after();
            rows.put(record.key(), new ProjectedOrder(
                    after.id(), after.status(), after.totalCents(), after.version()));
        }
        return gap ? ApplyResult.GAP_APPLIED : ApplyResult.APPLIED;
    }

    public synchronized Optional<ProjectedOrder> find(String id) {
        return Optional.ofNullable(rows.get(id));
    }

    public synchronized List<ProjectedOrder> all() {
        return rows.values().stream().sorted(java.util.Comparator.comparing(ProjectedOrder::id)).toList();
    }

    public synchronized long highestVersion(String id) {
        return highestVersionByKey.getOrDefault(id, 0L);
    }

    /** Administrative projection repair; deliberately bypasses event consumers and business effects. */
    public synchronized void repairFromSource(AuthoritativeOrder sourceRow) {
        rows.put(sourceRow.id(), new ProjectedOrder(
                sourceRow.id(), sourceRow.status(), sourceRow.totalCents(), sourceRow.version()));
        highestVersionByKey.put(sourceRow.id(), sourceRow.version());
    }

    /** Removes a row absent from the authoritative source without emitting a domain action. */
    public synchronized void removeOrphan(String id) {
        rows.remove(id);
    }

    private static void validate(CdcRecord record) {
        if (record == null
                || record.eventId() == null || record.eventId().isBlank()
                || record.key() == null || record.key().isBlank()
                || record.sourcePosition() < 0
                || record.sourceVersion() <= 0
                || record.operation() == null
                || record.origin() == null) {
            throw new PoisonCdcRecordException("CDC envelope is incomplete");
        }
        if (record.operation() == CdcRecord.Operation.DELETE && record.after() != null) {
            throw new PoisonCdcRecordException("DELETE must not contain after image");
        }
        if (record.operation() != CdcRecord.Operation.DELETE && record.after() == null) {
            throw new PoisonCdcRecordException(record.operation() + " requires after image");
        }
        if (record.after() != null
                && (!record.key().equals(record.after().id())
                || record.sourceVersion() != record.after().version())) {
            throw new PoisonCdcRecordException("key or source version does not match after image");
        }
    }

    public enum ApplyResult {
        APPLIED,
        GAP_APPLIED,
        DUPLICATE,
        STALE
    }

    public record ProjectedOrder(String id, String status, long totalCents, long sourceVersion) {
        AuthoritativeOrder asAuthoritative() {
            return new AuthoritativeOrder(id, status, totalCents, sourceVersion);
        }
    }

    public static final class PoisonCdcRecordException extends IllegalArgumentException {
        public PoisonCdcRecordException(String message) {
            super(message);
        }
    }
}
