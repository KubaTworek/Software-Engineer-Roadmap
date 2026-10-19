package com.example.paymentsystem.reconciliation;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import com.example.paymentsystem.payment.PaymentProvider;

@RestController
@RequestMapping("/v1/admin/reconciliation")
public class ReconciliationController {
    private final ReconciliationService service;
    private final SettlementReportImporter importer;
    private final SettlementReportService reports;
    public ReconciliationController(ReconciliationService service, SettlementReportImporter importer,
                                    SettlementReportService reports) {
        this.service = service;
        this.importer = importer;
        this.reports = reports;
    }

    @PostMapping
    Map<String, String> reconcile(@Valid @RequestBody ProviderObservation observation) {
        return Map.of("result", service.reconcile(observation));
    }

    @PostMapping(value = "/snapshot/{provider}", consumes = "text/csv")
    SettlementReportResult reconcileSnapshot(@PathVariable PaymentProvider provider, @RequestBody String csv) {
        return service.reconcileCompleteSnapshot(provider, importer.parseCompleteSnapshot(provider, csv));
    }

    /** Produkcyjna granica importu wymaga manifestu definiującego kompletność snapshotu. */
    @PostMapping(value = "/reports/{provider}", consumes = "text/csv")
    SettlementReportResult importReport(
            @PathVariable PaymentProvider provider,
            @RequestHeader("X-Report-Id") String reportId,
            @RequestHeader("X-Period-From") java.time.Instant periodFrom,
            @RequestHeader("X-Period-To") java.time.Instant periodTo,
            @RequestBody String csv) {
        return reports.importComplete(provider, reportId, periodFrom, periodTo, csv);
    }
}
