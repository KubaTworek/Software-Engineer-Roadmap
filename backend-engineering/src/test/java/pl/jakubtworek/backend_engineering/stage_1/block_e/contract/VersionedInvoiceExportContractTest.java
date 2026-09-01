package pl.jakubtworek.backend_engineering.stage_1.block_e.contract;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch.InvoiceBatchGenerator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VersionedInvoiceExportContractTest {

    private final InvoiceBatchGenerator generator = rows -> "unchanged-v1-bytes\n";
    private final VersionedInvoiceExportContract contract = new VersionedInvoiceExportContract(generator);

    @Test
    void oldConsumerKeepsItsStringContractUnchanged() {
        assertThat(contract.exportV1(List.of())).isEqualTo("unchanged-v1-bytes\n");
    }

    @Test
    void newConsumerOptsIntoAdditiveMetadataWithoutChangingContent() {
        InvoiceExportResponseV2 response = contract.exportV2(new InvoiceExportRequestV2(List.of(), true));

        assertThat(response.content()).isEqualTo("unchanged-v1-bytes\n");
        assertThat(response.mediaType()).isEqualTo("text/x-invoice-batch");
        assertThat(response.schemaVersion()).isEqualTo("2");
    }
}
