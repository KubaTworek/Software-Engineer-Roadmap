package com.example.videostreaming.transcoding;

import com.example.videostreaming.catalog.Video;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class TranscodingJobFencingTest {
    @Test
    void expiredLeaseCanBeReclaimedButOldOwnerCannotCommit() {
        TranscodingJob job = new TranscodingJob(new Video("title", "description", null), "raw/source.mp4");
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        long oldToken = job.claim(start, Duration.ofSeconds(10));
        long newToken = job.claim(start.plusSeconds(11), Duration.ofMinutes(1));

        assertThat(newToken).isGreaterThan(oldToken);
        assertThatThrownBy(() -> job.completed(oldToken, start.plusSeconds(12)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fencing");

        job.completed(newToken, start.plusSeconds(13));
        assertThat(job.getStatus()).isEqualTo(TranscodingJobStatus.COMPLETED);
    }

    @Test
    void activeLeaseRejectsSecondWorker() {
        TranscodingJob job = new TranscodingJob(new Video("title", "description", null), "raw/source.mp4");
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        job.claim(start, Duration.ofMinutes(1));

        assertThatThrownBy(() -> job.claim(start.plusSeconds(1), Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active lease");
    }
}
