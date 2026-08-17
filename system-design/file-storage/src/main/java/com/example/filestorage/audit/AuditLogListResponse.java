package com.example.filestorage.audit;

import java.util.List;

public record AuditLogListResponse(
        List<AuditLogResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
