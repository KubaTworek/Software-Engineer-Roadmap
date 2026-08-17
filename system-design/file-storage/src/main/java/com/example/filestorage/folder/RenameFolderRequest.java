package com.example.filestorage.folder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameFolderRequest(@NotBlank @Size(max = 255) String name) {}
