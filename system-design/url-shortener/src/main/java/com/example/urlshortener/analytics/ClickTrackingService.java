package com.example.urlshortener.analytics;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class ClickTrackingService {

    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public ClickTrackingService(ApplicationEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    public void track(String shortCode, HttpServletRequest request) {
        String ipAddress = clientIp(request);
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        String referrer = request.getHeader(HttpHeaders.REFERER);

        publisher.publishEvent(new ClickTrackedEvent(
            shortCode,
            Instant.now(clock),
            ipAddress,
            userAgent,
            referrer
        ));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
