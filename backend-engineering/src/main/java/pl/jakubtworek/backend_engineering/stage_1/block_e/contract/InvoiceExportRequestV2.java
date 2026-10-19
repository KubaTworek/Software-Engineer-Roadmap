package pl.jakubtworek.backend_engineering.stage_1.block_e.contract;

import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch.LegacyInvoiceRow;

import java.util.List;

public record InvoiceExportRequestV2(List<LegacyInvoiceRow> rows, boolean includeSchemaMetadata) {

    public InvoiceExportRequestV2 {
        rows = List.copyOf(rows);
    }
}
