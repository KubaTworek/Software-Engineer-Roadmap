package com.example.filestorage.upload;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dowodzi najważniejszej lokalnej gwarancji uploadu wieloczęściowego: plik nie
 * zostaje opublikowany przed zapisaniem dokładnie wszystkich zadeklarowanych części.
 */
class UploadSessionTest {

    @Test
    void completesOnlyAfterEveryDeclaredChunkWasUploaded() {
        UploadSession session = sessionWithTwoChunks();

        session.markChunkUploaded();

        assertThatThrownBy(session::markCompleted)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing chunks");

        session.markChunkUploaded();
        session.markCompleted();

        assertThat(session.getStatus()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(session.getUploadedChunks()).isEqualTo(2);
    }

    @Test
    void neverCountsMoreChunksThanDeclared() {
        UploadSession session = sessionWithTwoChunks();
        session.markChunkUploaded();
        session.markChunkUploaded();

        assertThatThrownBy(session::markChunkUploaded)
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getUploadedChunks()).isEqualTo(2);
    }

    private UploadSession sessionWithTwoChunks() {
        return new UploadSession(
                UUID.randomUUID(),
                null,
                "recording.mp4",
                "video/mp4",
                10,
                5,
                2,
                null,
                Instant.now().plus(1, ChronoUnit.HOURS)
        );
    }
}
