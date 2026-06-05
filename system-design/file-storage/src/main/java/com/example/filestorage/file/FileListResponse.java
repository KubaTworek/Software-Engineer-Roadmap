package com.example.filestorage.file;

import java.util.List;

public record FileListResponse(
        List<FileResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
