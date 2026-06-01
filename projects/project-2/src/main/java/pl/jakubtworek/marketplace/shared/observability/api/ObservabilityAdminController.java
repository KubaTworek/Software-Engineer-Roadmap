package pl.jakubtworek.marketplace.shared.observability.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.marketplace.integration.kafka.DlqEventRepository;
import pl.jakubtworek.marketplace.integration.kafka.DlqEventStatus;
import pl.jakubtworek.marketplace.shared.observability.FlowTraceRepository;
import pl.jakubtworek.marketplace.shared.observability.MarketplaceMetrics;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/observability")
public class ObservabilityAdminController {
    private final FlowTraceRepository traces;
    private final MarketplaceMetrics metrics;
    private final DlqEventRepository dlqRepository;

    public ObservabilityAdminController(FlowTraceRepository traces, MarketplaceMetrics metrics, DlqEventRepository dlqRepository) {
        this.traces = traces;
        this.metrics = metrics;
        this.dlqRepository = dlqRepository;
    }

    @GetMapping("/trace/correlation/{correlationId}")
    public ResponseEntity<?> traceByCorrelationId(@PathVariable UUID correlationId) {
        return ResponseEntity.ok(traces.findByCorrelationId(correlationId));
    }

    @GetMapping("/trace/order/{orderId}")
    public ResponseEntity<?> traceByOrderId(@PathVariable UUID orderId) {
        return ResponseEntity.ok(traces.findByOrderId(orderId));
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> metrics() {
        return ResponseEntity.ok(Map.of(
                "counters", metrics.counters(),
                "gauges", metrics.gauges(),
                "dlqNewCount", dlqRepository.findByStatus(DlqEventStatus.NEW, 1000).size()
        ));
    }

    @GetMapping("/dlq/reasons")
    public ResponseEntity<?> dlqReasons() {
        return ResponseEntity.ok(dlqRepository.findAll().stream()
                .map(event -> Map.of(
                        "id", event.id(),
                        "eventId", event.envelope().eventId(),
                        "eventType", event.envelope().eventType(),
                        "correlationId", event.envelope().correlationId(),
                        "reason", event.reason(),
                        "attempts", event.attempts(),
                        "status", event.status()
                ))
                .toList());
    }
}
