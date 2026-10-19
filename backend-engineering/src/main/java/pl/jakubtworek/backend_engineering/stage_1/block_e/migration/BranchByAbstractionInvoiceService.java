package pl.jakubtworek.backend_engineering.stage_1.block_e.migration;

import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch.InvoiceBatchGenerator;
import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch.LegacyInvoiceRow;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Keeps callers stable while the implementation is introduced, compared and switched. */
public final class BranchByAbstractionInvoiceService implements InvoiceBatchGenerator {

    public enum Branch {
        LEGACY,
        REPLACEMENT
    }

    private final InvoiceBatchGenerator legacy;
    private final InvoiceBatchGenerator replacement;
    private final AtomicReference<Branch> selected = new AtomicReference<>(Branch.LEGACY);

    public BranchByAbstractionInvoiceService(
            InvoiceBatchGenerator legacy,
            InvoiceBatchGenerator replacement) {
        this.legacy = legacy;
        this.replacement = replacement;
    }

    public void select(Branch branch) {
        selected.set(branch);
    }

    @Override
    public String export(List<LegacyInvoiceRow> rows) {
        return selected.get() == Branch.LEGACY
                ? legacy.export(rows)
                : replacement.export(rows);
    }
}
