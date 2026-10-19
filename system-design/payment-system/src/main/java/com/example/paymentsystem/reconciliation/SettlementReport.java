package com.example.paymentsystem.reconciliation;

import com.example.paymentsystem.payment.PaymentProvider;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Trwały manifest kompletnego raportu settlementowego. Sama lista wierszy nie
 * dowodzi kompletności; potrzebujemy tożsamości pliku, zakresu czasu i sumy
 * kontrolnej, aby nie uznać strony paginacji za pełny obraz providera.
 */
@Entity
@Table(name = "settlement_reports", uniqueConstraints =
        @UniqueConstraint(name = "uk_settlement_report_provider_external", columnNames = {"provider", "external_report_id"}))
public class SettlementReport {
    @Id
    @Column(name = "report_id", nullable = false)
    private String reportId;
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private PaymentProvider provider;
    @Column(name = "external_report_id", nullable = false)
    private String externalReportId;
    @Column(name = "period_from", nullable = false)
    private Instant periodFrom;
    @Column(name = "period_to", nullable = false)
    private Instant periodTo;
    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;
    @Column(name = "processed_at")
    private Instant processedAt;

    protected SettlementReport() { }

    public SettlementReport(String reportId, PaymentProvider provider, String externalReportId,
                            Instant periodFrom, Instant periodTo, String checksum) {
        if (!periodFrom.isBefore(periodTo)) throw new IllegalArgumentException("Settlement period must be non-empty");
        this.reportId = reportId;
        this.provider = provider;
        this.externalReportId = externalReportId;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.checksum = checksum;
        this.status = "IMPORTED";
        this.importedAt = Instant.now();
    }

    public void processed() {
        if (!"IMPORTED".equals(status)) throw new IllegalStateException("Report was already processed");
        status = "PROCESSED";
        processedAt = Instant.now();
    }

    public String getChecksum() { return checksum; }
    public String getStatus() { return status; }
}
