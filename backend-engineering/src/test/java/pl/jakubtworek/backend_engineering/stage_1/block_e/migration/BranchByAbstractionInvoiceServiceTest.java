package pl.jakubtworek.backend_engineering.stage_1.block_e.migration;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch.InvoiceBatchGenerator;
import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch.LegacyInvoiceBatchService;
import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch.LegacyInvoiceRow;
import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch.LegacyRuntime;
import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch.LegacyTaxRules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

class BranchByAbstractionInvoiceServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 15);
    private static final String BATCH_ID = "batch-2026-0001";

    @Test
    void replacementMatchesLegacyBeforeAndAfterTrafficSwitch() {
        try (MockedStatic<LegacyRuntime> runtime = fixedLegacyRuntime()) {
            InvoiceBatchGenerator legacy = new LegacyInvoiceBatchService();
            InvoiceBatchGenerator replacement = replacement();
            InvoiceBatchGenerator parity = new ParityCheckingInvoiceBatchGenerator(legacy, replacement);
            BranchByAbstractionInvoiceService facade =
                    new BranchByAbstractionInvoiceService(parity, replacement);

            String legacyResult = facade.export(rows());
            facade.select(BranchByAbstractionInvoiceService.Branch.REPLACEMENT);
            String replacementResult = facade.export(rows());

            assertThat(replacementResult).isEqualTo(legacyResult);
        }
    }

    @Test
    void shadowComparisonStopsMigrationWhenCandidateChangesOneObservableByte() {
        try (MockedStatic<LegacyRuntime> runtime = fixedLegacyRuntime()) {
            InvoiceBatchGenerator brokenCandidate = rows -> replacement().export(rows)
                    .replace("CUSTOMER", "CLIENT");
            InvoiceBatchGenerator parity = new ParityCheckingInvoiceBatchGenerator(
                    new LegacyInvoiceBatchService(), brokenCandidate);

            assertThatThrownBy(() -> parity.export(rows()))
                    .isInstanceOf(BehaviorChangedException.class)
                    .satisfies(exception -> {
                        BehaviorChangedException changed = (BehaviorChangedException) exception;
                        assertThat(changed.legacyOutput()).contains("CUSTOMER");
                        assertThat(changed.candidateOutput()).contains("CLIENT");
                    });
        }
    }

    private MockedStatic<LegacyRuntime> fixedLegacyRuntime() {
        MockedStatic<LegacyRuntime> runtime = mockStatic(LegacyRuntime.class);
        runtime.when(LegacyRuntime::today).thenReturn(DATE);
        runtime.when(LegacyRuntime::nextBatchId).thenReturn(BATCH_ID);
        return runtime;
    }

    private InvoiceBatchGenerator replacement() {
        return new RefactoredInvoiceBatchService(
                () -> DATE,
                () -> BATCH_ID,
                LegacyTaxRules::rateFor);
    }

    private List<LegacyInvoiceRow> rows() {
        return List.of(
                new LegacyInvoiceRow("Acme", "PL", new BigDecimal("10.00")),
                new LegacyInvoiceRow("Beta", "DE", new BigDecimal("0.05")));
    }
}
