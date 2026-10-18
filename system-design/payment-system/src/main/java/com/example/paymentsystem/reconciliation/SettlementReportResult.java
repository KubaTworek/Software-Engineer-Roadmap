package com.example.paymentsystem.reconciliation;

/** Summary returned by a complete provider settlement snapshot import. */
public record SettlementReportResult(int rows, int matched, int repaired, int breaks,
                                     int missingFromProvider) { }
