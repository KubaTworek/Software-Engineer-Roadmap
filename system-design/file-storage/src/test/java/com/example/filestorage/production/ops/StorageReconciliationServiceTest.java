package com.example.filestorage.production.ops;

import com.example.filestorage.file.FileMetadataRepository;
import com.example.filestorage.production.processing.FileProcessingJobRepository;
import com.example.filestorage.storage.StorageService;
import com.example.filestorage.upload.UploadChunkRepository;
import com.example.filestorage.version.FileVersionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorageReconciliationServiceTest {
    @Test
    void reportsOnlyOldUnreferencedObjects() {
        var files = mock(FileMetadataRepository.class);
        var findings = mock(StorageConsistencyFindingRepository.class);
        var storage = mock(StorageService.class);
        var versions = mock(FileVersionRepository.class);
        var chunks = mock(UploadChunkRepository.class);
        var jobs = mock(FileProcessingJobRepository.class);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var service = new StorageReconciliationService(files, findings, storage,
                Clock.fixed(now, ZoneOffset.UTC), versions, chunks, jobs);
        when(versions.findAllReferencedObjectKeys()).thenReturn(List.of("objects/known"));
        when(chunks.findAllReferencedObjectKeys()).thenReturn(List.of("uploads/in-progress"));
        when(jobs.findAllResultObjectKeys()).thenReturn(List.of());
        when(storage.listObjects("", 100)).thenReturn(List.of(
                new StorageService.ObjectSummary("objects/known", now.minus(Duration.ofDays(2)), 10),
                new StorageService.ObjectSummary("objects/orphan", now.minus(Duration.ofDays(2)), 10),
                new StorageService.ObjectSummary("objects/fresh", now.minus(Duration.ofMinutes(1)), 10)));

        var result = service.checkStorageInventory("", 100, Duration.ofHours(1));

        assertThat(result.orphanObjects()).isEqualTo(1);
        verify(findings, times(1)).save(any(StorageConsistencyFinding.class));
    }
}
