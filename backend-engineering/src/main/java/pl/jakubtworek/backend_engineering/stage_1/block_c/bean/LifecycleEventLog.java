package pl.jakubtworek.backend_engineering.stage_1.block_c.bean;

import java.util.ArrayList;
import java.util.List;

/** Rejestr zdarzeń pozwalający testować kolejność lifecycle bez analizy logów. */
public final class LifecycleEventLog {

    private final List<String> events = new ArrayList<>();

    public synchronized void record(String event) {
        events.add(event);
    }

    public synchronized List<String> snapshot() {
        return List.copyOf(events);
    }
}
