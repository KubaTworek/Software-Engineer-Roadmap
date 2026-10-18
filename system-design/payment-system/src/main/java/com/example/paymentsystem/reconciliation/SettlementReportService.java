package com.example.paymentsystem.reconciliation;

import com.example.paymentsystem.payment.PaymentProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/** Koordynuje idempotentny import manifestu i właściwą reconciliation. */
@Service
public class SettlementReportService {
    private final SettlementReportRepository reports;
    private final SettlementReportImporter importer;
    private final ReconciliationService reconciliation;

    public SettlementReportService(SettlementReportRepository reports, SettlementReportImporter importer,
                                   ReconciliationService reconciliation) {
        this.reports = reports;
        this.importer = importer;
        this.reconciliation = reconciliation;
    }

    @Transactional
    public SettlementReportResult importComplete(PaymentProvider provider, String externalReportId,
                                                 Instant periodFrom, Instant periodTo, String csv) {
        String checksum = sha256(csv);
        var previous = reports.findByProviderAndExternalReportId(provider, externalReportId);
        if (previous.isPresent()) {
            if (!previous.get().getChecksum().equals(checksum)) {
                throw new IllegalArgumentException("The same report id was reused with different content");
            }
            throw new IllegalStateException("Settlement report was already imported");
        }
        SettlementReport report = reports.save(new SettlementReport(
                provider.name() + ":" + externalReportId, provider, externalReportId, periodFrom, periodTo, checksum));
        SettlementReportResult result = reconciliation.reconcileCompleteSnapshot(
                provider, importer.parseCompleteSnapshot(provider, csv));
        report.processed();
        return result;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot checksum settlement report", error);
        }
    }
}
