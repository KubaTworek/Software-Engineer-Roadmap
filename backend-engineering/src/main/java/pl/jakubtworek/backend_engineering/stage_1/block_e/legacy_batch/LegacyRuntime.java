package pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch;

import java.time.LocalDate;
import java.util.UUID;

/** Static environment access intentionally kept as the characterization-test baseline. */
public final class LegacyRuntime {

    private LegacyRuntime() {
    }

    public static LocalDate today() {
        return LocalDate.now();
    }

    public static String nextBatchId() {
        return UUID.randomUUID().toString();
    }
}
