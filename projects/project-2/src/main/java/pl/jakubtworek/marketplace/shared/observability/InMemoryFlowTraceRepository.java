package pl.jakubtworek.marketplace.shared.observability;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class InMemoryFlowTraceRepository implements FlowTraceRepository {
    private final List<FlowTraceEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void append(FlowTraceEntry entry) {
        entries.add(entry);
    }

    @Override
    public List<FlowTraceEntry> findByCorrelationId(UUID correlationId) {
        return entries.stream().filter(e -> correlationId.equals(e.correlationId())).toList();
    }

    @Override
    public List<FlowTraceEntry> findByOrderId(UUID orderId) {
        return entries.stream().filter(e -> orderId.equals(e.orderId())).toList();
    }

    @Override
    public List<FlowTraceEntry> all() {
        return List.copyOf(entries);
    }

    public void clear() {
        entries.clear();
    }
}
