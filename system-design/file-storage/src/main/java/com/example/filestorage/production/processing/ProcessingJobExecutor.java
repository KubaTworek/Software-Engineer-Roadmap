package com.example.filestorage.production.processing;

import com.example.filestorage.file.FileMetadata;
import com.example.filestorage.file.FileMetadataRepository;
import com.example.filestorage.production.antivirus.AntivirusService;
import com.example.filestorage.production.dedupe.DeduplicationService;
import com.example.filestorage.production.outbox.OutboxService;
import com.example.filestorage.production.thumbnail.ThumbnailService;
import com.example.filestorage.search.SearchIndexService;
import com.example.filestorage.storage.StorageService;
import com.example.filestorage.production.ops.FileStorageMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Wykonuje dokładnie jeden wcześniej zclaimowany job i zapisuje jego wynik. */
@Service
public class ProcessingJobExecutor {
    private static final int MAX_ATTEMPTS = 5;
    private final FileProcessingJobRepository jobs;
    private final FileMetadataRepository files;
    private final StorageService storage;
    private final AntivirusService antivirus;
    private final ThumbnailService thumbnails;
    private final DeduplicationService deduplication;
    private final OutboxService outbox;
    private final SearchIndexService search;
    private final Clock clock;
    private final FileStorageMetrics metrics;
    private final FileProcessingService processing;

    public ProcessingJobExecutor(FileProcessingJobRepository jobs, FileMetadataRepository files,
                                 StorageService storage, AntivirusService antivirus,
                                 ThumbnailService thumbnails, DeduplicationService deduplication,
                                 OutboxService outbox, SearchIndexService search, Clock clock,
                                 FileStorageMetrics metrics, FileProcessingService processing) {
        this.jobs = jobs;
        this.files = files;
        this.storage = storage;
        this.antivirus = antivirus;
        this.thumbnails = thumbnails;
        this.deduplication = deduplication;
        this.outbox = outbox;
        this.search = search;
        this.clock = clock;
        this.metrics = metrics;
        this.processing = processing;
    }

    @Transactional
    public void execute(UUID jobId) {
        FileProcessingJob job = jobs.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Processing job not found"));
        if (job.getStatus() != FileProcessingJobStatus.RUNNING) {
            return;
        }
        try {
            FileMetadata file = files.findByIdAndDeletedAtIsNull(job.getFileId())
                    .orElseThrow(() -> new NoSuchElementException("File not found for processing job"));
            if (!job.getVersionId().equals(file.getCurrentVersionId())) {
                job.markCompleted("STALE_VERSION_SKIPPED");
                metrics.processingResult(job.getJobType().name(), "stale");
                return;
            }
            String result = switch (job.getJobType()) {
                case ANTIVIRUS_SCAN -> scan(file);
                case THUMBNAIL_GENERATION -> thumbnails.generateIfSupported(file);
                case DEDUPE_REGISTER -> deduplication.registerVersion(file).toString();
            };
            job.markCompleted(result);
            metrics.processingResult(job.getJobType().name(), "completed");
        } catch (Exception exception) {
            job.retryOrFail(message(exception), clock.instant(), MAX_ATTEMPTS);
            metrics.processingResult(job.getJobType().name(),
                    job.getStatus() == FileProcessingJobStatus.FAILED ? "failed" : "retry");
        }
    }

    private String scan(FileMetadata file) {
        final com.example.filestorage.production.antivirus.AntivirusService.ScanResult result;
        try (var content = storage.download(file.getObjectKey())) {
            result = antivirus.scan(content);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not close antivirus input stream", exception);
        }
        if (!result.clean()) {
            file.reject();
            outbox.enqueue("FILE_REJECTED", "FILE", file.getId(), "{}");
            return "REJECTED: " + result.details();
        }
        if (!file.isDownloadable()) {
            file.markAvailable();
            processing.enqueuePostScanJobs(file.getId(), file.getCurrentVersionId());
            outbox.enqueue("FILE_AVAILABLE", "FILE", file.getId(), "{}");
            afterCommit(() -> search.reindexFileAsync(file.getId()));
        }
        return result.details();
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    private String message(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
