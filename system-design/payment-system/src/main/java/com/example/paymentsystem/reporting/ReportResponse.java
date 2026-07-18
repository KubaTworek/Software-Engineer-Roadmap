package com.example.paymentsystem.reporting;

public record ReportResponse(
        long totalPayments,
        long succeededPayments,
        long failedPayments,
        long totalVolume,
        long platformFees,
        long merchantNet,
        long stripeMockPayments,
        long adyenMockPayments,
        long payuMockPayments
) {
}
