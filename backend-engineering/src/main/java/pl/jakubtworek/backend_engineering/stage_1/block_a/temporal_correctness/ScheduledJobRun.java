package pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness;

import java.time.Instant;

public record ScheduledJobRun(
        String jobName,
        Instant scheduledAt,
        String executionKey,
        long fencingToken
) {

    static ScheduledJobRun create(String jobName, Instant scheduledAt, long fencingToken) {
        return new ScheduledJobRun(
                jobName,
                scheduledAt,
                jobName + "/" + scheduledAt,
                fencingToken
        );
    }
}
