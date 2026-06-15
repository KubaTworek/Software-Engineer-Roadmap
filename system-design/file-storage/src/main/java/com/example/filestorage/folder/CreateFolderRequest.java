package com.example.filestorage.folder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateFolderRequest(
        UUID parentFolderId,
        @NotBlank @Size(max = 255) String name
) {}
