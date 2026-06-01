package pl.jakubtworek.marketplace.shared.observability;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MarketplaceMetrics {
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public void increment(String name) {
        increment(name, 1);
    }

    public void increment(String name, long delta) {
        counters.computeIfAbsent(name, ignored -> new AtomicLong()).addAndGet(delta);
    }

    public long counter(String name) {
        return counters.getOrDefault(name, new AtomicLong()).get();
    }

    public void gauge(String name, long value) {
        gauges.computeIfAbsent(name, ignored -> new AtomicLong()).set(value);
    }

    public long gauge(String name) {
        return gauges.getOrDefault(name, new AtomicLong()).get();
    }

    public Map<String, Long> counters() {
        return counters.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    public Map<String, Long> gauges() {
        return gauges.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    public void clear() {
        counters.clear();
        gauges.clear();
    }
}
