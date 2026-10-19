package pl.jakubtworek.backend_engineering.stage_2.block_b.cdc_reconciliation;

public record CdcRecord(
        String eventId,
        int partition,
        long sourcePosition,
        String key,
        Operation operation,
        AuthoritativeOrder before,
        AuthoritativeOrder after,
        long sourceVersion,
        Origin origin
) {

    public enum Operation {
        READ,
        CREATE,
        UPDATE,
        DELETE
    }

    public enum Origin {
        SNAPSHOT,
        STREAM
    }
}
