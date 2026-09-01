package pl.jakubtworek.backend_engineering.stage_1.block_e.migration;

import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch.InvoiceBatchGenerator;
import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch.LegacyInvoiceRow;

import java.util.List;

/** Shadow-comparison step used before routing production output to the replacement. */
public final class ParityCheckingInvoiceBatchGenerator implements InvoiceBatchGenerator {

    private final InvoiceBatchGenerator legacy;
    private final InvoiceBatchGenerator candidate;

    public ParityCheckingInvoiceBatchGenerator(
            InvoiceBatchGenerator legacy,
            InvoiceBatchGenerator candidate) {
        this.legacy = legacy;
        this.candidate = candidate;
    }

    @Override
    public String export(List<LegacyInvoiceRow> rows) {
        String legacyOutput = legacy.export(rows);
        String candidateOutput = candidate.export(rows);
        if (!legacyOutput.equals(candidateOutput)) {
            throw new BehaviorChangedException(legacyOutput, candidateOutput);
        }
        return legacyOutput;
    }
}
