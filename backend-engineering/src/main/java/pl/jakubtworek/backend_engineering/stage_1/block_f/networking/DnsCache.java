package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A deterministic model of positive and negative DNS caching. */
public final class DnsCache {

    @FunctionalInterface
    public interface Resolver {
        List<InetAddress> resolve(String hostname);
    }

    private final Clock clock;
    private final Duration positiveTtl;
    private final Duration negativeTtl;
    private final Resolver resolver;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public DnsCache(Clock clock, Duration positiveTtl, Duration negativeTtl, Resolver resolver) {
        this.clock = clock;
        this.positiveTtl = requirePositive(positiveTtl, "positiveTtl");
        this.negativeTtl = requirePositive(negativeTtl, "negativeTtl");
        this.resolver = resolver;
    }

    public List<InetAddress> resolve(String hostname) {
        if (hostname == null || hostname.isBlank()) throw new IllegalArgumentException("hostname is required");
        Instant now = clock.instant();
        Entry cached = entries.get(hostname);
        if (cached != null && now.isBefore(cached.expiresAt())) return cached.addresses();

        List<InetAddress> resolved = List.copyOf(resolver.resolve(hostname));
        Duration ttl = resolved.isEmpty() ? negativeTtl : positiveTtl;
        entries.put(hostname, new Entry(resolved, now.plus(ttl)));
        return resolved;
    }

    public void invalidate(String hostname) {
        entries.remove(hostname);
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private record Entry(List<InetAddress> addresses, Instant expiresAt) {}
}
