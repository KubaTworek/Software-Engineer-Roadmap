package com.example.newsfeed.reconciliation;

public record ReconciliationReport(String projection, int scanned, int repaired, String limitation) { }
