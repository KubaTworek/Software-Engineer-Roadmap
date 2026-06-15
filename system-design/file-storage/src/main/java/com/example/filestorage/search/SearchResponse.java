package com.example.filestorage.search;

import java.util.List;

public record SearchResponse(
        List<SearchResultResponse> results,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
