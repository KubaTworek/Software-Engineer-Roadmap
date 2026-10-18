package com.example.filestorage.upload;

import com.example.filestorage.file.FileResponse;
import com.example.filestorage.storage.StorageService;
import com.example.filestorage.production.ops.FileStorageMetrics;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Saga finalizacji uploadu. Object storage nie uczestniczy w transakcji SQL,
 * dlatego operacja jest wznawialna, używa deterministycznego klucza finalnego,
 * a cleanup chunków następuje dopiero po zatwierdzeniu metadanych.
 */
@Service
public class UploadFinalizationCoordinator {
    private final UploadFinalizationDatabase database;
    private final UploadChunkRepository chunks;
    private final StorageService storage;
    private final FileStorageMetrics metrics;

    public UploadFinalizationCoordinator(
            UploadFinalizationDatabase database,
            UploadChunkRepository chunks,
            StorageService storage,
            FileStorageMetrics metrics
    ) {
        this.database = database;
        this.chunks = chunks;
        this.storage = storage;
        this.metrics = metrics;
    }

    public FileResponse complete(UUID userId, UUID uploadId) {
        var plan = database.prepare(userId, uploadId);
        if (plan.alreadyCompleted()) {
            return FileResponse.from(plan.completedFile());
        }

        if (plan.chunkObjectKeys().size() == 1) {
            storage.copy(plan.finalObjectKey(), plan.chunkObjectKeys().getFirst(), plan.contentType());
        } else {
            storage.compose(plan.finalObjectKey(), plan.chunkObjectKeys(), plan.contentType());
        }

        String actualHash = sha256(storage.download(plan.finalObjectKey()));
        if (plan.expectedSha256() != null && !plan.expectedSha256().equals(actualHash)) {
            storage.delete(plan.finalObjectKey());
            database.reject(userId, uploadId);
            throw new IllegalArgumentException("Final file SHA-256 mismatch");
        }

        FileResponse response = database.commit(plan, actualHash);
        metrics.uploadFinalized(plan.totalSizeBytes());

        // Cleanup jest bezpieczny dopiero po commicie. Jego niepowodzenie nie
        // cofa opublikowanego pliku; okresowy GC może ponowić usunięcie.
        for (String chunk : plan.chunkObjectKeys()) {
            try {
                storage.delete(chunk);
            } catch (RuntimeException ignored) {
                // Orphan temporary chunks are observable/recoverable GC work.
            }
        }
        chunks.deleteAllByUploadSessionId(uploadId);
        return response;
    }

    private String sha256(InputStream stream) {
        try (InputStream input = stream) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1;) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate final SHA-256", exception);
        }
    }
}
