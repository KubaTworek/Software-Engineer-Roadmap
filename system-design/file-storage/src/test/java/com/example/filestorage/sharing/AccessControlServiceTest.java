package com.example.filestorage.sharing;

import com.example.filestorage.file.FileMetadata;
import com.example.filestorage.file.FileMetadataRepository;
import com.example.filestorage.folder.FolderRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** ACL i publikacja to dwie niezalezne bramki; posiadanie roli nie omija kwarantanny. */
class AccessControlServiceTest {
    private final FileMetadataRepository files = mock(FileMetadataRepository.class);
    private final ResourcePermissionRepository permissions = mock(ResourcePermissionRepository.class);
    private final AccessControlService access = new AccessControlService(
            files, mock(FolderRepository.class), permissions);

    @Test
    void directEditorIncludesViewerButQuarantineStillBlocksDownload() {
        UUID owner = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        FileMetadata file = new FileMetadata(owner, null, "file.txt", "file.txt",
                "text/plain", 4, "objects/1", "hash");
        file.quarantine();
        ResourcePermission permission = mock(ResourcePermission.class);
        when(permission.isActive()).thenReturn(true);
        when(permission.getRole()).thenReturn(PermissionRole.EDITOR);
        when(files.findByIdAndDeletedAtIsNull(file.getId())).thenReturn(Optional.of(file));
        when(permissions.findByResourceTypeAndResourceIdAndGranteeUserId(
                ResourceType.FILE, file.getId(), actor)).thenReturn(Optional.of(permission));

        assertThat(access.requireFileRole(actor, file.getId(), PermissionRole.VIEWER)).isSameAs(file);
        assertThatThrownBy(() -> access.requireDownloadableFile(actor, file.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available");
    }
}
