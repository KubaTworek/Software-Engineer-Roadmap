package pl.jakubtworek.marketplace.shared.observability;

import java.util.List;
import java.util.UUID;

public interface FlowTraceRepository {
    void append(FlowTraceEntry entry);
    List<FlowTraceEntry> findByCorrelationId(UUID correlationId);
    List<FlowTraceEntry> findByOrderId(UUID orderId);
    List<FlowTraceEntry> all();
}
