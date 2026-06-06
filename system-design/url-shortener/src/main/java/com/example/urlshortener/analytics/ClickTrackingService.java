package com.example.urlshortener.analytics;

import com.example.urlshortener.queue.ClickEventPublisher;
import com.example.urlshortener.queue.ClickMessage;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class ClickTrackingService {

    private final ClickEventPublisher publisher;
    private final Clock clock;

    public ClickTrackingService(ClickEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    public void track(String shortCode, HttpServletRequest request) {
        publisher.publish(new ClickMessage(
            UUID.randomUUID().toString(),
            shortCode,
            Instant.now(clock),
            clientIp(request),
            request.getHeader(HttpHeaders.USER_AGENT),
            request.getHeader(HttpHeaders.REFERER),
            request.getHeader("CF-IPCountry"),
            requestId(request)
        ));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return requestId == null || requestId.isBlank() ? null : requestId;
    }
}
