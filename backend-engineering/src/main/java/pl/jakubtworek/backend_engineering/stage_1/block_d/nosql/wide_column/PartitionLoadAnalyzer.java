package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.wide_column;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Prosty detektor nierównego rozkładu używany przed wyborem partition key. */
public final class PartitionLoadAnalyzer {

    private PartitionLoadAnalyzer() {
    }

    public static LoadReport analyze(Collection<String> partitionKeys) {
        if (partitionKeys == null || partitionKeys.isEmpty()) {
            throw new IllegalArgumentException("partitionKeys must not be empty");
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String key : partitionKeys) {
            counts.merge(key, 1L, Long::sum);
        }
        Map.Entry<String, Long> busiest = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();
        return new LoadReport(
                Map.copyOf(counts),
                busiest.getKey(),
                (double) busiest.getValue() / partitionKeys.size()
        );
    }

    public record LoadReport(Map<String, Long> writesPerPartition, String busiestPartition, double busiestShare) {
        public boolean hasHotPartition(double maximumAcceptedShare) {
            if (maximumAcceptedShare <= 0 || maximumAcceptedShare > 1) {
                throw new IllegalArgumentException("maximumAcceptedShare must be in range (0, 1]");
            }
            return busiestShare > maximumAcceptedShare;
        }
    }
}
