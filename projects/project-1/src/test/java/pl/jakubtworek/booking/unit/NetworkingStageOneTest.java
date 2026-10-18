package pl.jakubtworek.booking.unit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.booking.education.network.ConnectionPoolGate;
import pl.jakubtworek.booking.education.network.CachingDnsResolver;
import pl.jakubtworek.booking.education.network.ConnectionReuseModel;
import pl.jakubtworek.booking.education.network.HalfOpenConnection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkingStageOneTest {

    @Test
    void dnsAddressRemainsStaleUntilTtlExpiresOrCacheIsInvalidated() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-13T10:00:00Z"));
        var authoritativeAddress = new java.util.concurrent.atomic.AtomicReference<>("10.0.0.1");
        CachingDnsResolver resolver = new CachingDnsResolver(
                ignored -> authoritativeAddress.get(),
                clock,
                Duration.ofSeconds(30)
        );

        assertThat(resolver.resolve("payments.internal")).isEqualTo("10.0.0.1");
        authoritativeAddress.set("10.0.0.2");
        assertThat(resolver.resolve("payments.internal"))
                .as("zmiana autorytatywnego DNS nie omija ważnego cache klienta")
                .isEqualTo("10.0.0.1");

        clock.advance(Duration.ofSeconds(30));
        assertThat(resolver.resolve("payments.internal")).isEqualTo("10.0.0.2");
    }

    @Test
    void keepAliveAndHttp2ReduceTlsHandshakesAndEphemeralPortChurn() {
        ConnectionReuseModel withoutReuse = new ConnectionReuseModel(false, 20, 100);
        ConnectionReuseModel reusedPool = new ConnectionReuseModel(true, 20, 100);

        assertThat(withoutReuse.tlsHandshakesForBurst(200, ConnectionReuseModel.HttpProtocol.HTTP_1_1))
                .isEqualTo(200);
        assertThat(reusedPool.tlsHandshakesForBurst(200, ConnectionReuseModel.HttpProtocol.HTTP_1_1))
                .isEqualTo(20);
        assertThat(reusedPool.tlsHandshakesForBurst(200, ConnectionReuseModel.HttpProtocol.HTTP_2))
                .isEqualTo(2);
    }

    @Test
    void halfOpenConnectionLooksHealthyUntilFirstIo() {
        HalfOpenConnection connection = new HalfOpenConnection();
        connection.remoteDisappearsWithoutCloseFrame();

        assertThat(connection.isLocallyOpen()).isTrue();
        assertThatThrownBy(() -> connection.write(new byte[]{1}))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("during I/O");
        assertThat(connection.isLocallyOpen()).isFalse();
    }

    @Test
    void poolAcquireHasItsOwnBudgetBeforeConnectAndRead() throws Exception {
        ConnectionPoolGate pool = new ConnectionPoolGate(1);
        try (var occupied = pool.acquire(Duration.ofMillis(20))) {
            assertThatThrownBy(() -> pool.acquire(Duration.ofMillis(20)))
                    .isInstanceOf(ConnectionPoolGate.PoolAcquireTimeoutException.class);
        }
        try (var availableAgain = pool.acquire(Duration.ofMillis(20))) {
            assertThat(availableAgain).isNotNull();
        }
    }

    @Test
    void clientDeadlineDoesNotAutomaticallyCancelDownstreamWork() throws Exception {
        AtomicBoolean downstreamFinished = new AtomicBoolean();
        CountDownLatch downstreamStarted = new CountDownLatch(1);
        CountDownLatch downstreamCompleted = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/payment", exchange -> {
            try {
                downstreamStarted.countDown();
                try {
                    Thread.sleep(Duration.ofMillis(200));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                downstreamFinished.set(true);
                downstreamCompleted.countDown();
                exchange.sendResponseHeaders(204, -1);
            } finally {
                exchange.close();
            }
        });
        server.start();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    // Jawny IPv4 usuwa z laboratorium niezależną zmienną DNS/IPv6;
                    // koszt i cache DNS są osobnym kontraktem sieciowym.
                    .uri(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/payment"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            var response = client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            assertThat(downstreamStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> response.orTimeout(50, TimeUnit.MILLISECONDS).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(TimeoutException.class);
            assertThat(downstreamCompleted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(downstreamFinished).isTrue();
        } finally {
            server.stop(0);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
