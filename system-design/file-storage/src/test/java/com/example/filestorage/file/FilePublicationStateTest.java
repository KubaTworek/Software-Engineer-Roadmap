package com.example.filestorage.file;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Kwarantanna jest granica bezpieczenstwa, nie tylko opisowym statusem. */
class FilePublicationStateTest {

    @Test
    void onlyCleanQuarantinedFileCanBecomeDownloadable() {
        FileMetadata file = file();
        file.quarantine();
        assertThat(file.isDownloadable()).isFalse();

        file.markAvailable();
        assertThat(file.isDownloadable()).isTrue();
    }

    @Test
    void rejectedFileCannotBePublishedByAccident() {
        FileMetadata file = file();
        file.quarantine();
        file.reject();

        assertThatThrownBy(file::markAvailable).isInstanceOf(IllegalStateException.class);
        assertThat(file.isDownloadable()).isFalse();
    }

    private FileMetadata file() {
        return new FileMetadata(UUID.randomUUID(), null, "report.pdf", "report.pdf",
                "application/pdf", 10, "users/u/blobs/b", "sha256");
    }
}
