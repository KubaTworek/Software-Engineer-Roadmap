package com.example.filestorage.sync;

import java.util.List;

public record SyncChangesResponse(
        List<ChangeLogResponse> changes,
        Long nextCursor,
        boolean hasMore
) {}
