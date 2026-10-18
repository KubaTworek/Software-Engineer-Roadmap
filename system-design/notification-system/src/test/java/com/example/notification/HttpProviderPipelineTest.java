package com.example.notification;

import com.example.notification.application.NotificationService;
import com.example.notification.application.NotificationWorker;
import com.example.notification.application.OutboxPublisher;
import com.example.notification.application.Ports;
import com.example.notification.domain.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Pełny flow intention -> outbox -> job -> prawdziwa granica HTTP. */
@SpringBootTest(properties = {"notification.worker.fixed-delay-ms=3600000", "notification.outbox.fixed-delay-ms=3600000"})
@ActiveProfiles("http-provider")
class HttpProviderPipelineTest {
    private static final CopyOnWriteArrayList<String> IDEMPOTENCY_KEYS = new CopyOnWriteArrayList<>();
    private static final HttpServer PROVIDER = server();

    private static HttpServer server() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/messages", exchange -> {
                IDEMPOTENCY_KEYS.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().add("X-Provider-Message-Id", "http-message-1");
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
            });
            server.start(); return server;
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    @DynamicPropertySource static void providerUrls(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + PROVIDER.getAddress().getPort() + "/messages";
        registry.add("notification.provider.email-url", () -> url);
        registry.add("notification.provider.sms-url", () -> url);
        registry.add("notification.provider.push-url", () -> url);
        registry.add("notification.provider.in-app-url", () -> url);
    }

    @AfterAll static void stop() { PROVIDER.stop(0); }
    @Autowired NotificationService notifications;
    @Autowired OutboxPublisher outbox;
    @Autowired NotificationWorker worker;
    @Autowired Ports.NotificationJobRepository jobs;

    @Test void completePipelineUsesStableIdempotencyKeyAtHttpBoundary() {
        IDEMPOTENCY_KEYS.clear();
        var accepted = notifications.create("tenant-a", "test", "user", NotificationType.PAYMENT_FAILED,
                List.of(Channel.EMAIL), new ContactPoint("user@example.com", null, null),
                Map.of("firstName", "A", "invoiceId", "I", "amount", "10"), "http-" + UUID.randomUUID(), null);
        outbox.publishPending();
        NotificationJob job = jobs.findByNotificationId(accepted.notification().getId()).getFirst();
        worker.processJob(job.getId());

        assertThat(job.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(job.getProviderMessageId()).isEqualTo("http-message-1");
        assertThat(IDEMPOTENCY_KEYS).containsExactly(job.getId().toString());
    }
}
