package pl.jakubtworek.backend_engineering.stage_1.block_e.migration;

import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch.InvoiceBatchGenerator;
import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch.LegacyInvoiceRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** New implementation behind the old stable abstraction; output parity is intentional. */
public final class RefactoredInvoiceBatchService implements InvoiceBatchGenerator {

    private final BusinessDateProvider dateProvider;
    private final BatchIdGenerator batchIdGenerator;
    private final TaxPolicy taxPolicy;

    public RefactoredInvoiceBatchService(
            BusinessDateProvider dateProvider,
            BatchIdGenerator batchIdGenerator,
            TaxPolicy taxPolicy) {
        this.dateProvider = dateProvider;
        this.batchIdGenerator = batchIdGenerator;
        this.taxPolicy = taxPolicy;
    }

    @Override
    public String export(List<LegacyInvoiceRow> rows) {
        StringBuilder output = new StringBuilder()
                .append("BATCH|").append(batchIdGenerator.nextId()).append('|')
                .append(dateProvider.currentDate()).append('\n')
                .append("NO|CUSTOMER|COUNTRY|NET|TAX|GROSS\n");

        BigDecimal totalNet = BigDecimal.ZERO.setScale(2);
        BigDecimal totalTax = BigDecimal.ZERO.setScale(2);
        for (int index = 0; index < rows.size(); index++) {
            LegacyInvoiceRow row = rows.get(index);
            BigDecimal net = money(row.netAmount());
            BigDecimal tax = money(net.multiply(taxPolicy.rateFor(row.country())));

            appendRow(output, index + 1, row, net, tax);
            totalNet = totalNet.add(net);
            totalTax = totalTax.add(tax);
        }

        output.append("TOTAL|||NET=").append(money(totalNet))
                .append("|TAX=").append(money(totalTax))
                .append("|GROSS=").append(money(totalNet.add(totalTax)))
                .append('\n');
        return output.toString();
    }

    private void appendRow(
            StringBuilder output,
            int number,
            LegacyInvoiceRow row,
            BigDecimal net,
            BigDecimal tax) {
        output.append(number).append('|')
                .append(row.customer()).append('|')
                .append(row.country()).append('|')
                .append(net).append('|')
                .append(tax).append('|')
                .append(net.add(tax)).append('\n');
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
