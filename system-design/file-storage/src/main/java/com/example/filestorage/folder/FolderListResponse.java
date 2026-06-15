package com.example.filestorage.folder;

import java.util.List;

public record FolderListResponse(
        List<FolderResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
