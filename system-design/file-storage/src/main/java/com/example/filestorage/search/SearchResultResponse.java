package com.example.filestorage.search;

import com.example.filestorage.sharing.ResourceType;
import java.time.Instant;
import java.util.UUID;

public record SearchResultResponse(
        ResourceType resourceType,
        UUID resourceId,
        String name,
        String contentType,
        Long sizeBytes,
        Instant updatedAt
) {
    public static SearchResultResponse from(SearchIndex item) {
        return new SearchResultResponse(item.getResourceType(), item.getResourceId(), item.getName(), item.getContentType(), item.getSizeBytes(), item.getUpdatedAt());
    }
}
