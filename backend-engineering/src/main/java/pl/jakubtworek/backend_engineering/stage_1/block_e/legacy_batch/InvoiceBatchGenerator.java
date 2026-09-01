package pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch;

import java.util.List;

/** Stable abstraction introduced before replacing the legacy implementation. */
public interface InvoiceBatchGenerator {

    String export(List<LegacyInvoiceRow> rows);
}
