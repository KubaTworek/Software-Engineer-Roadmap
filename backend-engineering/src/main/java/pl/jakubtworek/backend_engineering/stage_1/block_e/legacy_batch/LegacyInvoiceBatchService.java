package pl.jakubtworek.backend_engineering.stage_1.block_e.legacy_batch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Deliberately procedural code with static dependencies and a byte-sensitive export contract.
 * Refactoring starts by observing it, not by immediately making it prettier.
 */
public class LegacyInvoiceBatchService implements InvoiceBatchGenerator {

    @Override
    public String export(List<LegacyInvoiceRow> rows) {
        StringBuilder output = new StringBuilder();
        output.append("BATCH|")
                .append(LegacyRuntime.nextBatchId())
                .append('|')
                .append(LegacyRuntime.today())
                .append('\n');
        output.append("NO|CUSTOMER|COUNTRY|NET|TAX|GROSS\n");

        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        int number = 1;
        for (LegacyInvoiceRow row : rows) {
            BigDecimal net = row.netAmount().setScale(2, RoundingMode.HALF_UP);
            BigDecimal tax = net.multiply(LegacyTaxRules.rateFor(row.country()))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal gross = net.add(tax);

            output.append(number++).append('|')
                    .append(row.customer()).append('|')
                    .append(row.country()).append('|')
                    .append(net).append('|')
                    .append(tax).append('|')
                    .append(gross).append('\n');
            totalNet = totalNet.add(net);
            totalTax = totalTax.add(tax);
        }

        output.append("TOTAL|||NET=").append(totalNet.setScale(2, RoundingMode.HALF_UP))
                .append("|TAX=").append(totalTax.setScale(2, RoundingMode.HALF_UP))
                .append("|GROSS=").append(totalNet.add(totalTax).setScale(2, RoundingMode.HALF_UP))
                .append('\n');
        return output.toString();
    }
}
