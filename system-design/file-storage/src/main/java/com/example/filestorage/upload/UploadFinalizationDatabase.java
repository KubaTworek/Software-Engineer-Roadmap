package com.example.filestorage.upload;

import com.example.filestorage.auth.UserRepository;
import com.example.filestorage.file.FileMetadata;
import com.example.filestorage.file.FileMetadataRepository;
import com.example.filestorage.file.FileResponse;
import com.example.filestorage.production.outbox.OutboxService;
import com.example.filestorage.production.processing.FileProcessingService;
import com.example.filestorage.sharing.ResourceType;
import com.example.filestorage.sync.ChangeLogService;
import com.example.filestorage.version.FileVersion;
import com.example.filestorage.version.FileVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Krótkie transakcje bazodanowe otaczające zewnętrzny krok object storage.
 * Rozdzielenie tej klasy od koordynatora jest celowe: wywołanie przez proxy
 * Springa naprawdę kończy transakcję przed kosztownym compose/download.
 */
@Service
public class UploadFinalizationDatabase {
    private final UploadSessionRepository sessions;
    private final UploadChunkRepository chunks;
    private final FileMetadataRepository files;
    private final FileVersionRepository versions;
    private final UserRepository users;
    private final ChangeLogService changes;
    private final FileProcessingService processing;
    private final OutboxService outbox;
    private final Clock clock;

    public UploadFinalizationDatabase(
            UploadSessionRepository sessions,
            UploadChunkRepository chunks,
            FileMetadataRepository files,
            FileVersionRepository versions,
            UserRepository users,
            ChangeLogService changes,
            FileProcessingService processing,
            OutboxService outbox,
            Clock clock
    ) {
        this.sessions = sessions;
        this.chunks = chunks;
        this.files = files;
        this.versions = versions;
        this.users = users;
        this.changes = changes;
        this.processing = processing;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public FinalizationPlan prepare(UUID userId, UUID uploadId) {
        UploadSession session = sessions.findByIdAndUserIdForUpdate(uploadId, userId)
                .orElseThrow(() -> new NoSuchElementException("Upload session not found"));

        if (session.getStatus() == UploadStatus.COMPLETED) {
            FileMetadata file = files.findById(session.getFinalFileId())
                    .orElseThrow(() -> new IllegalStateException("Completed upload has no file metadata"));
            return FinalizationPlan.completed(file);
        }

        List<UploadChunk> uploaded = chunks.findAllByUploadSessionIdOrderByChunkIndexAsc(uploadId);
        if (uploaded.size() != session.getTotalChunks()) {
            throw new IllegalStateException("Upload is incomplete");
        }
        for (int index = 0; index < uploaded.size(); index++) {
            if (uploaded.get(index).getChunkIndex() != index) {
                throw new IllegalStateException("Upload has missing or unordered chunks");
            }
        }

        String finalObjectKey = session.getFinalObjectKey();
        if (session.getStatus() != UploadStatus.ASSEMBLING) {
            finalObjectKey = "users/%s/blobs/%s".formatted(userId, uploadId);
            session.startAssembling(finalObjectKey, clock.instant());
            sessions.save(session);
        }

        return new FinalizationPlan(
                uploadId, userId, session.getParentFolderId(), session.getFilename(),
                session.getContentType(), session.getTotalSizeBytes(), session.getExpectedSha256(),
                finalObjectKey, uploaded.stream().map(UploadChunk::getObjectKey).toList(), null);
    }

    @Transactional
    public FileResponse commit(FinalizationPlan plan, String finalSha256) {
        UploadSession session = sessions.findByIdAndUserIdForUpdate(plan.uploadId(), plan.userId())
                .orElseThrow(() -> new NoSuchElementException("Upload session not found"));
        if (session.getStatus() == UploadStatus.COMPLETED) {
            return files.findById(session.getFinalFileId()).map(FileResponse::from)
                    .orElseThrow(() -> new IllegalStateException("Completed upload has no file metadata"));
        }
        if (session.getStatus() != UploadStatus.ASSEMBLING
                || !plan.finalObjectKey().equals(session.getFinalObjectKey())) {
            throw new IllegalStateException("Upload assembly state changed");
        }

        FileMetadata file = new FileMetadata(
                plan.userId(), plan.parentFolderId(), plan.filename(), plan.filename(),
                plan.contentType(), plan.totalSizeBytes(), plan.finalObjectKey(), finalSha256);
        file.quarantine();
        file = files.save(file);

        FileVersion version = versions.save(new FileVersion(
                file.getId(), 1, plan.finalObjectKey(), plan.contentType(), plan.totalSizeBytes(),
                finalSha256, plan.userId(), false));
        file.setInitialVersion(version.getId());
        files.save(file);

        var user = users.findByIdForUpdate(plan.userId())
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        user.commitReservedStorage(plan.totalSizeBytes());
        users.save(user);

        changes.record(plan.userId(), plan.userId(), ResourceType.FILE, file.getId(),
                "FILE_QUARANTINED", "{\"uploadId\":\"" + plan.uploadId() + "\"}");
        processing.enqueueSecurityGate(file.getId(), version.getId());
        outbox.enqueue("FILE_UPLOADED", "FILE", file.getId(),
                "{\"fileId\":\"" + file.getId() + "\"}");

        session.markCompleted(file.getId(), clock.instant());
        sessions.save(session);
        return FileResponse.from(file);
    }

    @Transactional
    public void reject(UUID userId, UUID uploadId) {
        UploadSession session = sessions.findByIdAndUserIdForUpdate(uploadId, userId)
                .orElseThrow(() -> new NoSuchElementException("Upload session not found"));
        if (session.getStatus() == UploadStatus.COMPLETED) {
            return;
        }
        session.markFailed(clock.instant());
        sessions.save(session);
        users.findByIdForUpdate(userId).ifPresent(user -> {
            user.releaseReservedStorage(session.getTotalSizeBytes());
            users.save(user);
        });
    }

    public record FinalizationPlan(
            UUID uploadId,
            UUID userId,
            UUID parentFolderId,
            String filename,
            String contentType,
            long totalSizeBytes,
            String expectedSha256,
            String finalObjectKey,
            List<String> chunkObjectKeys,
            FileMetadata completedFile
    ) {
        static FinalizationPlan completed(FileMetadata file) {
            return new FinalizationPlan(null, null, null, null, null, 0, null, null, List.of(), file);
        }

        public boolean alreadyCompleted() {
            return completedFile != null;
        }
    }
}
