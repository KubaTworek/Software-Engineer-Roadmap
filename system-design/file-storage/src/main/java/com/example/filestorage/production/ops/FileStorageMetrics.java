package com.example.filestorage.production.ops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Metryki odpowiadaja pytaniom operacyjnym, a nie liczbie wywolan metod.
 * Tagi maja zamkniety zbior wartosci; fileId/userId nigdy nie trafiaja do
 * etykiet Prometheusa, bo spowodowaloby to nieograniczona cardinality.
 */
@Component
public class FileStorageMetrics {
    private final MeterRegistry registry;
    private final DistributionSummary finalizedBytes;

    public FileStorageMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.finalizedBytes = DistributionSummary.builder("file_storage.upload.finalized.bytes")
                .baseUnit("bytes")
                .description("Rozklad rozmiaru poprawnie sfinalizowanych plikow")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void uploadFinalized(long bytes) {
        finalizedBytes.record(bytes);
        registry.counter("file_storage.upload.finalized.total").increment();
    }

    public void processingResult(String jobType, String outcome) {
        Counter.builder("file_storage.processing.total")
                .tag("job_type", jobType)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public void outboxDelivery(String outcome) {
        registry.counter("file_storage.outbox.delivery.total", "outcome", outcome).increment();
    }
}
