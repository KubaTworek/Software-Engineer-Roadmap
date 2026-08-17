package com.example.filestorage.quota;

public record QuotaResponse(
        long usedBytes,
        long quotaBytes,
        long remainingBytes,
        double usedPercent
) {}
