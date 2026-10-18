package com.example.urlshortener.service;

public record ProjectionReconciliationReport(
        int scanned,
        int matching,
        int missing,
        int stale,
        int divergent,
        int aheadOfSource,
        int repaired
) {}
