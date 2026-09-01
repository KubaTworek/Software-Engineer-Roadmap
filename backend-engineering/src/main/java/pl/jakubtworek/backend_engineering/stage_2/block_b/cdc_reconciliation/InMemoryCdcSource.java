package pl.jakubtworek.backend_engineering.stage_2.block_b.cdc_reconciliation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Models a consistent database snapshot and a monotonic WAL/LSN change log. */
public final class InMemoryCdcSource {

    private final int partitions;
    private final Map<String, AuthoritativeOrder> rows = new HashMap<>();
    private final List<CdcRecord> log = new ArrayList<>();
    private long position;

    public InMemoryCdcSource(int partitions) {
        if (partitions <= 0) {
            throw new IllegalArgumentException("partitions must be positive");
        }
        this.partitions = partitions;
    }

    public synchronized AuthoritativeOrder create(String id, String status, long totalCents) {
        if (rows.containsKey(id)) {
            throw new IllegalStateException("order already exists: " + id);
        }
        AuthoritativeOrder created = new AuthoritativeOrder(id, status, totalCents, 1);
        rows.put(id, created);
        append(id, CdcRecord.Operation.CREATE, null, created);
        return created;
    }

    public synchronized AuthoritativeOrder update(String id, String status, long totalCents) {
        AuthoritativeOrder before = require(id);
        AuthoritativeOrder updated = new AuthoritativeOrder(id, status, totalCents, before.version() + 1);
        rows.put(id, updated);
        append(id, CdcRecord.Operation.UPDATE, before, updated);
        return updated;
    }

    public synchronized void delete(String id) {
        AuthoritativeOrder before = require(id);
        rows.remove(id);
        append(id, CdcRecord.Operation.DELETE, before, null);
    }

    public synchronized CdcSnapshot beginSnapshot() {
        List<AuthoritativeOrder> snapshotRows = rows.values().stream()
                .sorted(Comparator.comparing(AuthoritativeOrder::id))
                .toList();
        return new CdcSnapshot(position, snapshotRows);
    }

    public synchronized List<CdcRecord> changesAfter(long exclusivePosition) {
        return log.stream()
                .filter(record -> record.sourcePosition() > exclusivePosition)
                .toList();
    }

    public synchronized List<AuthoritativeOrder> currentRows() {
        return rows.values().stream().sorted(Comparator.comparing(AuthoritativeOrder::id)).toList();
    }

    public synchronized long currentPosition() {
        return position;
    }

    public int partitions() {
        return partitions;
    }

    private void append(
            String id,
            CdcRecord.Operation operation,
            AuthoritativeOrder before,
            AuthoritativeOrder after
    ) {
        long nextPosition = ++position;
        long version = after == null ? before.version() + 1 : after.version();
        log.add(new CdcRecord(
                "wal:" + nextPosition,
                Math.floorMod(id.hashCode(), partitions),
                nextPosition,
                id,
                operation,
                before,
                after,
                version,
                CdcRecord.Origin.STREAM
        ));
    }

    private AuthoritativeOrder require(String id) {
        AuthoritativeOrder order = rows.get(id);
        if (order == null) {
            throw new IllegalArgumentException("unknown order " + id);
        }
        return order;
    }
}
