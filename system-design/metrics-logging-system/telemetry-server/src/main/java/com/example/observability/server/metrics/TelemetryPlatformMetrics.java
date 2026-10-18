package com.example.observability.server.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Metryki samej platformy są celowo niskokardynalne. Tenant, traceId i nazwa
 * metryki użytkownika nie są tagami, ponieważ stworzyłyby drugi problem
 * telemetryczny podczas obserwowania pierwszego.
 */
@Component
public class TelemetryPlatformMetrics {
    private final MeterRegistry registry;
    private final Map<String, Counter> consumed = new ConcurrentHashMap<>();

    public TelemetryPlatformMetrics(MeterRegistry registry) { this.registry = registry; }

    public void consumed(String signal, int records) {
        consumed.computeIfAbsent(signal, value -> Counter.builder("telemetry.consumer.records")
                .tag("signal", value).description("Records durably processed from Kafka")
                .register(registry)).increment(records);
    }

    public void checkpointSaved() {
        registry.counter("telemetry.consumer.offset.commits").increment();
    }
}
