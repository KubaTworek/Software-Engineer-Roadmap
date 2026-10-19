package pl.jakubtworek.backend_engineering.stage_2.block_b.cdc_reconciliation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Poison data stops one partition until an operator explicitly quarantines it. */
public final class PartitionCdcProcessor {

    private final ProjectionPipeline pipeline;
    private final Set<RecordKey> quarantined = new HashSet<>();
    private final List<QuarantineEntry> quarantineLog = new ArrayList<>();

    public PartitionCdcProcessor(ProjectionPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public BatchResult process(
            int partition,
            List<CdcRecord> records,
            ProjectionPipeline.Purpose purpose
    ) {
        long lastCommittedPosition = -1;
        int applied = 0;
        for (CdcRecord record : records) {
            if (record.partition() != partition) {
                continue;
            }
            RecordKey key = RecordKey.from(record);
            if (quarantined.contains(key)) {
                lastCommittedPosition = record.sourcePosition();
                continue;
            }
            try {
                pipeline.process(record, purpose);
                applied++;
                lastCommittedPosition = record.sourcePosition();
            } catch (OrderProjectionStore.PoisonCdcRecordException exception) {
                return new BatchResult(
                        Status.BLOCKED_BY_POISON, lastCommittedPosition, applied, record, exception.getMessage());
            }
        }
        return new BatchResult(Status.COMPLETED, lastCommittedPosition, applied, null, null);
    }

    public void quarantine(CdcRecord record, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("quarantine reason is required");
        }
        RecordKey key = RecordKey.from(record);
        if (quarantined.add(key)) {
            quarantineLog.add(new QuarantineEntry(record, reason));
        }
    }

    public List<QuarantineEntry> quarantineLog() {
        return List.copyOf(quarantineLog);
    }

    public enum Status {
        COMPLETED,
        BLOCKED_BY_POISON
    }

    public record BatchResult(
            Status status,
            long lastCommittedPosition,
            int processedRecords,
            CdcRecord poisonRecord,
            String failure
    ) {
    }

    public record QuarantineEntry(CdcRecord record, String reason) {
    }

    private record RecordKey(int partition, long position) {
        static RecordKey from(CdcRecord record) {
            return new RecordKey(record.partition(), record.sourcePosition());
        }
    }
}
