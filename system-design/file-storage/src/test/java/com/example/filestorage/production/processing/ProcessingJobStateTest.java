package com.example.filestorage.production.processing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pokazuje lease/retry jako maszyne stanow zamiast niejawnej petli workera. */
class ProcessingJobStateTest {
    @Test
    void failedAttemptIsDelayedAndEventuallyBecomesTerminal() {
        FileProcessingJob job = new FileProcessingJob(
                UUID.randomUUID(), UUID.randomUUID(), FileProcessingJobType.ANTIVIRUS_SCAN);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        job.claim(now);
        job.retryOrFail("scanner unavailable", now, 2);
        assertThat(job.getStatus()).isEqualTo(FileProcessingJobStatus.PENDING);
        assertThat(job.getNextAttemptAt()).isAfter(now);

        job.claim(now.plusSeconds(3));
        job.retryOrFail("scanner unavailable", now.plusSeconds(3), 2);
        assertThat(job.getStatus()).isEqualTo(FileProcessingJobStatus.FAILED);
        assertThat(job.getAttempts()).isEqualTo(2);
    }
}
