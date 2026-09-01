package pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class LegacyInvoiceBatchGoldenMasterTest {

    @Test
    void capturesTimeRandomnessStaticRulesAndTheCompleteExternalFormat() throws IOException {
        try (MockedStatic<LegacyRuntime> runtime = mockStatic(LegacyRuntime.class);
             MockedStatic<LegacyTaxRules> taxRules = mockStatic(LegacyTaxRules.class, CALLS_REAL_METHODS)) {
            runtime.when(LegacyRuntime::today).thenReturn(LocalDate.of(2026, 6, 15));
            runtime.when(LegacyRuntime::nextBatchId).thenReturn("batch-2026-0001");

            String actual = new LegacyInvoiceBatchService().export(rows());

            assertThat(actual).isEqualTo(goldenMaster());
            runtime.verify(LegacyRuntime::today);
            runtime.verify(LegacyRuntime::nextBatchId);
            taxRules.verify(() -> LegacyTaxRules.rateFor("PL"));
            taxRules.verify(() -> LegacyTaxRules.rateFor("DE"));
        }
    }

    private String goldenMaster() throws IOException {
        try (var input = getClass().getResourceAsStream("/stage_1/block_e/invoice-batch.golden")) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static List<LegacyInvoiceRow> rows() {
        return List.of(
                new LegacyInvoiceRow("Acme", "PL", new BigDecimal("10.00")),
                new LegacyInvoiceRow("Beta", "DE", new BigDecimal("0.05")));
    }
}
