package com.example.paymentsystem.reporting;

import java.util.Map;

public record ReportResponse(
        long totalPayments,
        long succeededPayments,
        long failedPayments,
        Map<String, Long> capturedVolumeByCurrency,
        Map<String, Long> platformFeesByCurrency,
        Map<String, Long> merchantNetByCurrency,
        long stripeMockPayments,
        long adyenMockPayments,
        long payuMockPayments
) {
}
