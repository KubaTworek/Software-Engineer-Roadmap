package com.example.urlshortener.analytics;

import com.example.urlshortener.queue.ClickEventPublisher;
import com.example.urlshortener.queue.ClickMessage;
import com.example.urlshortener.security.TrustedClientAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/** Captures a small event on the latency-sensitive redirect path. */
@Service
public class ClickTrackingService {
    private final ClickEventPublisher publisher;
    private final Clock clock;
    private final TrustedClientAddressResolver clients;

    public ClickTrackingService(ClickEventPublisher publisher, Clock clock, TrustedClientAddressResolver clients) {
        this.publisher = publisher;
        this.clock = clock;
        this.clients = clients;
    }

    public void track(String shortCode, HttpServletRequest request) {
        publisher.publish(new ClickMessage(
                UUID.randomUUID().toString(), shortCode, Instant.now(clock), clients.resolve(request),
                request.getHeader(HttpHeaders.USER_AGENT), request.getHeader(HttpHeaders.REFERER),
                request.getHeader("CF-IPCountry"), normalized(request.getHeader("X-Request-Id"))));
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.substring(0, Math.min(128, value.length()));
    }
}
