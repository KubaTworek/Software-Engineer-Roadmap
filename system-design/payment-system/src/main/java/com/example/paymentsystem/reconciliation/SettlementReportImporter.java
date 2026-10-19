package com.example.paymentsystem.reconciliation;

import com.example.paymentsystem.payment.PaymentProvider;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Minimal CSV boundary for an educational settlement report. Production code
 * should use the provider schema, checksums, object-store metadata and a real
 * CSV parser supporting quoting. The domain receives normalized observations.
 */
@Component
public class SettlementReportImporter {
    public List<ProviderObservation> parseCompleteSnapshot(PaymentProvider provider, String csv) {
        String[] lines = csv.strip().split("\\R");
        if (lines.length == 0 || !lines[0].equals("payment_id,provider_payment_id,status,amount,currency")) {
            throw new IllegalArgumentException("Unsupported settlement report schema");
        }
        List<ProviderObservation> observations = new ArrayList<>();
        for (int index = 1; index < lines.length; index++) {
            if (lines[index].isBlank()) continue;
            String[] fields = lines[index].split(",", -1);
            if (fields.length != 5) throw new IllegalArgumentException("Invalid settlement row " + index);
            observations.add(new ProviderObservation(provider,
                    fields[0].isBlank() ? null : UUID.fromString(fields[0]), fields[1], fields[2],
                    Long.parseLong(fields[3]), fields[4]));
        }
        return observations;
    }
}
