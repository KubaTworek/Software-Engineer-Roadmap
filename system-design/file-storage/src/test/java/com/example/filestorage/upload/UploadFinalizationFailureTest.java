package com.example.filestorage.upload;

import com.example.filestorage.file.FileMetadata;
import com.example.filestorage.file.FileResponse;
import com.example.filestorage.production.ops.FileStorageMetrics;
import com.example.filestorage.storage.StorageService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Kontrolowana awaria po zapisie storage dowodzi, ze retry uzywa tego samego klucza. */
class UploadFinalizationFailureTest {
    @Test
    void retryAfterDatabaseFailureReusesDeterministicObjectAndCleansChunksOnlyAfterCommit() throws Exception {
        UploadFinalizationDatabase database = mock(UploadFinalizationDatabase.class);
        UploadChunkRepository chunks = mock(UploadChunkRepository.class);
        StorageService storage = mock(StorageService.class);
        FileStorageMetrics metrics = mock(FileStorageMetrics.class);
        UploadFinalizationCoordinator coordinator = new UploadFinalizationCoordinator(database, chunks, storage, metrics);
        UUID uploadId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        byte[] content = "complete-content".getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        var plan = new UploadFinalizationDatabase.FinalizationPlan(
                uploadId, userId, null, "file.txt", "text/plain", content.length,
                hash, "users/u/blobs/" + uploadId, List.of("uploads/u/0"), null);
        FileMetadata file = new FileMetadata(userId, null, "file.txt", "file.txt",
                "text/plain", content.length, plan.finalObjectKey(), hash);
        FileResponse response = FileResponse.from(file);

        when(database.prepare(userId, uploadId)).thenReturn(plan);
        when(storage.download(plan.finalObjectKey()))
                .thenAnswer(invocation -> new ByteArrayInputStream(content));
        when(database.commit(plan, hash))
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(response);

        assertThatThrownBy(() -> coordinator.complete(userId, uploadId))
                .hasMessageContaining("database unavailable");
        verify(storage, never()).delete("uploads/u/0");

        assertThat(coordinator.complete(userId, uploadId)).isEqualTo(response);
        verify(storage, times(2)).copy(plan.finalObjectKey(), "uploads/u/0", "text/plain");
        verify(storage).delete("uploads/u/0");
        verify(chunks).deleteAllByUploadSessionId(uploadId);
    }
}
