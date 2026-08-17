package com.example.filestorage.version;

import java.util.List;

public record FileVersionListResponse(
        List<FileVersionResponse> versions,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
