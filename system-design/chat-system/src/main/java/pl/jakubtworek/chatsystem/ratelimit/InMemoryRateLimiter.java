package pl.jakubtworek.chatsystem.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class InMemoryRateLimiter {
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean allow(String key, int maxRequests, long windowSeconds) {
        long now = Instant.now().getEpochSecond();
        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || now >= existing.windowStartEpochSecond + windowSeconds) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.counter.incrementAndGet();
            return existing;
        });
        return window.counter.get() <= maxRequests;
    }

    private record Window(long windowStartEpochSecond, AtomicInteger counter) {}
}
