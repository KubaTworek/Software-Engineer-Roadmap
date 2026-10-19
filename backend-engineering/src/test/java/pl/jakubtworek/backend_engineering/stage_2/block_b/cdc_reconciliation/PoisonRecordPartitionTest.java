package pl.jakubtworek.backend_engineering.stage_2.block_b.cdc_reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PoisonRecordPartitionTest {

    @Test
    void poisonRecordStopsPartitionUntilExplicitQuarantineThenProcessingContinues() {
        OrderProjectionStore projection = new OrderProjectionStore();
        PartitionCdcProcessor processor = new PartitionCdcProcessor(new ProjectionPipeline(
                projection, ProjectionPipeline.BusinessEffect.none()));
        CdcRecord first = upsert("event-1", 1, "order-1", "NEW", 1);
        CdcRecord poison = new CdcRecord(
                "event-2", 0, 2, "order-bad", CdcRecord.Operation.UPDATE,
                null, null, 1, CdcRecord.Origin.STREAM);
        CdcRecord third = upsert("event-3", 3, "order-3", "PAID", 1);
        List<CdcRecord> partition = List.of(first, poison, third);

        PartitionCdcProcessor.BatchResult blocked = processor.process(
                0, partition, ProjectionPipeline.Purpose.LIVE);

        assertThat(blocked.status()).isEqualTo(PartitionCdcProcessor.Status.BLOCKED_BY_POISON);
        assertThat(blocked.lastCommittedPosition()).isEqualTo(1);
        assertThat(projection.find("order-3")).isEmpty();

        processor.quarantine(poison, "schema violation reviewed by operator");
        PartitionCdcProcessor.BatchResult resumed = processor.process(
                0, partition, ProjectionPipeline.Purpose.LIVE);

        assertThat(resumed.status()).isEqualTo(PartitionCdcProcessor.Status.COMPLETED);
        assertThat(resumed.lastCommittedPosition()).isEqualTo(3);
        assertThat(projection.find("order-3")).isPresent();
        assertThat(processor.quarantineLog()).singleElement()
                .extracting(PartitionCdcProcessor.QuarantineEntry::reason)
                .isEqualTo("schema violation reviewed by operator");
    }

    private static CdcRecord upsert(String eventId, long position, String id, String status, long version) {
        AuthoritativeOrder after = new AuthoritativeOrder(id, status, 1_000, version);
        return new CdcRecord(
                eventId, 0, position, id, CdcRecord.Operation.UPDATE,
                null, after, version, CdcRecord.Origin.STREAM);
    }
}
