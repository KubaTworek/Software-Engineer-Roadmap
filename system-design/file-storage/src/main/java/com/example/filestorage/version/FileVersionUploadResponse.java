package com.example.filestorage.version;

import com.example.filestorage.file.FileResponse;

public record FileVersionUploadResponse(
        FileResponse file,
        FileVersionResponse version,
        boolean conflict,
        String message
) {}
