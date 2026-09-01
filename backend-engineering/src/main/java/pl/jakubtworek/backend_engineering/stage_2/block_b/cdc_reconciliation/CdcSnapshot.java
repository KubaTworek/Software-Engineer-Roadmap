package pl.jakubtworek.backend_engineering.stage_2.block_b.cdc_reconciliation;

import java.util.List;

public record CdcSnapshot(long highWatermark, List<AuthoritativeOrder> rows) {

    public CdcSnapshot {
        rows = List.copyOf(rows);
    }

    public List<CdcRecord> records(int partitions) {
        return rows.stream()
                .map(row -> new CdcRecord(
                        "snapshot:" + highWatermark + ':' + row.id(),
                        Math.floorMod(row.id().hashCode(), partitions),
                        highWatermark,
                        row.id(),
                        CdcRecord.Operation.READ,
                        null,
                        row,
                        row.version(),
                        CdcRecord.Origin.SNAPSHOT
                ))
                .toList();
    }
}
