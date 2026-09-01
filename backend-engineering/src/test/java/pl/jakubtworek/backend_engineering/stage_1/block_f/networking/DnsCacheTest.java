package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DnsCacheTest {

    @Test
    void keepsOldAddressUntilPositiveTtlExpires() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AtomicReference<List<InetAddress>> authoritative =
                new AtomicReference<>(List.of(InetAddress.getByName("192.0.2.10")));
        DnsCache cache = new DnsCache(clock, Duration.ofSeconds(30), Duration.ofSeconds(5), host -> authoritative.get());

        assertThat(cache.resolve("orders.internal")).extracting(InetAddress::getHostAddress).containsExactly("192.0.2.10");
        authoritative.set(List.of(InetAddress.getByName("192.0.2.11")));

        assertThat(cache.resolve("orders.internal")).extracting(InetAddress::getHostAddress).containsExactly("192.0.2.10");
        clock.advance(Duration.ofSeconds(30));
        assertThat(cache.resolve("orders.internal")).extracting(InetAddress::getHostAddress).containsExactly("192.0.2.11");
    }

    @Test
    void negativeResultHasItsOwnShorterTtl() throws Exception {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        AtomicReference<List<InetAddress>> authoritative = new AtomicReference<>(List.of());
        DnsCache cache = new DnsCache(clock, Duration.ofMinutes(1), Duration.ofSeconds(2), host -> authoritative.get());

        assertThat(cache.resolve("new-service.internal")).isEmpty();
        authoritative.set(List.of(InetAddress.getByName("192.0.2.20")));
        clock.advance(Duration.ofSeconds(2));

        assertThat(cache.resolve("new-service.internal")).hasSize(1);
    }
}
