package pl.jakubtworek.booking.education.network;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mały, deterministyczny model cache DNS. Pokazuje ważną granicę: zmiana adresu
 * instancji w DNS nie przełącza natychmiast klienta, który nadal ma ważny wpis
 * TTL. Produkcyjny resolver ma więcej poziomów cache (OS, JVM, proxy), ale
 * niezmiennik pozostaje ten sam: wynik może być poprawny według TTL i zarazem
 * wskazywać już niedostępną instancję.
 */
public final class CachingDnsResolver {
    private final AddressLookup lookup;
    private final Clock clock;
    private final Duration ttl;
    private final Map<String, CacheEntry> cache = new HashMap<>();

    public CachingDnsResolver(AddressLookup lookup, Clock clock, Duration ttl) {
        this.lookup = Objects.requireNonNull(lookup);
        this.clock = Objects.requireNonNull(clock);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.ttl = ttl;
    }

    public synchronized String resolve(String host) {
        Instant now = clock.instant();
        CacheEntry cached = cache.get(host);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.address();
        }

        String address = lookup.resolve(host);
        cache.put(host, new CacheEntry(address, now.plus(ttl)));
        return address;
    }

    public synchronized void invalidate(String host) {
        cache.remove(host);
    }

    @FunctionalInterface
    public interface AddressLookup {
        String resolve(String host);
    }

    private record CacheEntry(String address, Instant expiresAt) {
    }
}
