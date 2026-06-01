package pl.jakubtworek.marketplace.shared.observability;

import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

public final class MdcScope implements AutoCloseable {
    private final Map<String, String> previousValues = new HashMap<>();
    private boolean closed;

    private MdcScope() {}

    public static MdcScope open() {
        return new MdcScope();
    }

    public MdcScope put(String key, Object value) {
        if (closed) throw new IllegalStateException("MDC scope is already closed");
        previousValues.putIfAbsent(key, MDC.get(key));
        if (value == null) MDC.remove(key);
        else MDC.put(key, value.toString());
        return this;
    }

    @Override
    public void close() {
        if (closed) return;
        previousValues.forEach((key, previous) -> {
            if (previous == null) MDC.remove(key);
            else MDC.put(key, previous);
        });
        closed = true;
    }
}
