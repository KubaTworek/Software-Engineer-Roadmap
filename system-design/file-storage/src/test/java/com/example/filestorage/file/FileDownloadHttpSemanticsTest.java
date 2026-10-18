package com.example.filestorage.file;

import com.example.filestorage.audit.AuditService;
import com.example.filestorage.auth.UserRepository;
import com.example.filestorage.folder.FolderService;
import com.example.filestorage.production.dedupe.DeduplicationService;
import com.example.filestorage.production.outbox.OutboxService;
import com.example.filestorage.production.processing.FileProcessingService;
import com.example.filestorage.search.SearchIndexService;
import com.example.filestorage.sharing.AccessControlService;
import com.example.filestorage.storage.StorageService;
import com.example.filestorage.sync.ChangeLogService;
import com.example.filestorage.version.FileVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FileDownloadHttpSemanticsTest {
    private final StorageService storage = mock(StorageService.class);
    private final AccessControlService access = mock(AccessControlService.class);
    private final FileService service = new FileService(
            mock(FileMetadataRepository.class), storage, mock(FolderService.class), mock(UserRepository.class),
            access, mock(AuditService.class), mock(FileVersionRepository.class), mock(ChangeLogService.class),
            mock(SearchIndexService.class), mock(DeduplicationService.class),
            mock(FileProcessingService.class), mock(OutboxService.class));

    @Test
    void singleRangeReturns206AndExactContentRange() {
        UUID actor = UUID.randomUUID();
        FileMetadata file = file();
        when(access.requireDownloadableFile(actor, file.getId())).thenReturn(file);
        when(storage.downloadRange(file.getObjectKey(), 2, 4))
                .thenReturn(new ByteArrayInputStream(new byte[] {2, 3, 4, 5}));

        var response = service.download(actor, file.getId(), "bytes=2-5", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 2-5/10");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(4);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"sha256\"");
    }

    @Test
    void matchingEtagReturns304WithoutOpeningStorageStream() {
        UUID actor = UUID.randomUUID();
        FileMetadata file = file();
        when(access.requireDownloadableFile(actor, file.getId())).thenReturn(file);

        var response = service.download(actor, file.getId(), null, "\"sha256\"");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        verifyNoInteractions(storage);
    }

    private FileMetadata file() {
        return new FileMetadata(UUID.randomUUID(), null, "ten.bin", "ten.bin",
                "application/octet-stream", 10, "objects/ten", "sha256");
    }
}
