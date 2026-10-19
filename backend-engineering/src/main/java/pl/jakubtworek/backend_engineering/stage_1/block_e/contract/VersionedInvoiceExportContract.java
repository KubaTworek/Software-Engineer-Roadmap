package pl.jakubtworek.backend_engineering.stage_1.block_e.contract;

import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch.InvoiceBatchGenerator;
import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch.LegacyInvoiceRow;

import java.util.List;

/** Adds a richer V2 response while retaining the byte-identical V1 contract. */
public final class VersionedInvoiceExportContract {

    private static final String MEDIA_TYPE = "text/x-invoice-batch";
    private static final String SCHEMA_VERSION = "2";

    private final InvoiceBatchGenerator generator;

    public VersionedInvoiceExportContract(InvoiceBatchGenerator generator) {
        this.generator = generator;
    }

    public String exportV1(List<LegacyInvoiceRow> rows) {
        return generator.export(rows);
    }

    public InvoiceExportResponseV2 exportV2(InvoiceExportRequestV2 request) {
        return new InvoiceExportResponseV2(
                generator.export(request.rows()),
                MEDIA_TYPE,
                request.includeSchemaMetadata() ? SCHEMA_VERSION : null);
    }
}
