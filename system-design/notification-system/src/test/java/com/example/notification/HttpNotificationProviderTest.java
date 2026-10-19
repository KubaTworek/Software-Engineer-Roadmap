package com.example.notification;

import com.example.notification.application.Exceptions;
import com.example.notification.domain.*;
import com.example.notification.infrastructure.provider.HttpNotificationProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class HttpNotificationProviderTest {
    @Test void sendsStableOperationKeyAndReadsProviderId() throws Exception {
        AtomicReference<String> key = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            key.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            exchange.getResponseHeaders().add("X-Provider-Message-Id", "provider-42");
            exchange.sendResponseHeaders(202, -1); exchange.close();
        });
        try {
            var provider = provider(server, Duration.ofSeconds(1));
            assertThat(provider.send("tenant", "a@example.com", new RenderedNotification("s", "b"), "op-42")
                    .providerMessageId()).isEqualTo("provider-42");
            assertThat(key).hasValue("op-42");
        } finally { server.stop(0); }
    }

    @Test void classifiesExplicitRejectionAsNotSentAndResponseTimeoutAsUnknown() throws Exception {
        HttpServer rejected = server(exchange -> { exchange.sendResponseHeaders(429, -1); exchange.close(); });
        try {
            assertThatThrownBy(() -> provider(rejected, Duration.ofSeconds(1)).send("t", "a@b.pl",
                    new RenderedNotification("s", "b"), "op"))
                    .isInstanceOfSatisfying(Exceptions.ProviderCallException.class,
                            ex -> assertThat(ex.certainty()).isEqualTo(Exceptions.OutcomeCertainty.NOT_SENT));
        } finally { rejected.stop(0); }

        HttpServer slow = server(exchange -> {
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.sendResponseHeaders(202, -1); exchange.close();
        });
        try {
            assertThatThrownBy(() -> provider(slow, Duration.ofMillis(30)).send("t", "a@b.pl",
                    new RenderedNotification("s", "b"), "op"))
                    .isInstanceOfSatisfying(Exceptions.ProviderCallException.class,
                            ex -> assertThat(ex.certainty()).isEqualTo(Exceptions.OutcomeCertainty.UNKNOWN));
        } finally { slow.stop(0); }
    }

    private HttpNotificationProvider provider(HttpServer server, Duration timeout) {
        return new HttpNotificationProvider(Channel.EMAIL, "http-test",
                URI.create("http://localhost:" + server.getAddress().getPort() + "/messages"),
                HttpClient.newHttpClient(), timeout);
    }
    private HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/messages", handler); server.start(); return server;
    }
}
