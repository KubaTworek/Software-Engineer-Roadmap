package com.example.urlshortener.analytics;

import com.example.urlshortener.abuse.AbuseDetectionService;
import com.example.urlshortener.abuse.AbuseVerdict;
import com.example.urlshortener.queue.ClickMessage;
import com.example.urlshortener.security.SecretSupport;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materializes an at-least-once click stream into raw events and daily aggregates.
 *
 * <p>The database transaction is the correctness boundary: acquiring the unique
 * {@code event_id} marker and incrementing the aggregate either both commit or
 * both roll back. Redis counters are updated only from an after-commit event, so
 * they are an explicitly rebuildable projection rather than the source of truth.</p>
 */
@Service
public class AnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final ClickEventRepository clickEvents;
    private final ApplicationEventPublisher eventPublisher;
    private final AnalyticsProperties properties;
    private final AbuseDetectionService abuseDetection;
    private final ClickAnalyticsWriter writer;

    public AnalyticsService(
            ClickEventRepository clickEvents,
            ApplicationEventPublisher eventPublisher,
            AnalyticsProperties properties,
            AbuseDetectionService abuseDetection,
            ClickAnalyticsWriter writer
    ) {
        this.clickEvents = clickEvents;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.abuseDetection = abuseDetection;
        this.writer = writer;
    }

    /**
     * Persists one delivery. A preliminary lookup avoids unnecessary work for the
     * common duplicate case, but it is not a concurrency guarantee. The conditional
     * insert in {@link ClickAnalyticsWriter} is the authoritative deduplication step.
     */
    @Transactional
    public void processClick(ClickMessage message) {
        if (!properties.enabled()) {
            return;
        }

        try {
            if (clickEvents.existsByEventId(message.eventId())) {
                log.debug("Skipping duplicate click eventId={}", message.eventId());
                return;
            }

            AbuseVerdict verdict = abuseDetection.evaluate(message);
            LocalDate date = message.clickedAt().atZone(ZoneOffset.UTC).toLocalDate();
            ClickEvent click = new ClickEvent(
                    message.eventId(),
                    message.shortCode(),
                    message.clickedAt(),
                    hashIp(message.ipAddress()),
                    truncate(message.userAgent(), 2048),
                    truncate(message.referrer(), 2048),
                    truncate(referrerDomain(message.referrer()), 255),
                    normalizeCountry(message.country()),
                    verdict.deviceType(),
                    verdict.browser(),
                    verdict.suspicious(),
                    truncate(verdict.reason(), 2048)
            );

            if (!writer.insertIfAbsent(click)) {
                log.debug("Skipping concurrently delivered duplicate eventId={}", message.eventId());
                return;
            }

            // A database-side increment avoids lost updates between Rabbit consumers.
            writer.incrementDaily(message.shortCode(), date);
            eventPublisher.publishEvent(new ClickAnalyticsCommitted(message.shortCode(), date));
        } catch (Exception failure) {
            // Rethrowing is intentional: the listener must reject/retry or dead-letter
            // the delivery instead of acknowledging partially processed analytics.
            log.warn("Failed to persist queued click analytics eventId={} shortCode={}",
                    message.eventId(), message.shortCode(), failure);
            throw new IllegalStateException(failure);
        }
    }

    /**
     * HMAC pseudonymizes an IP without permitting an offline IPv4 dictionary attack
     * unless the secret is known. A production rotation needs a persisted key version.
     */
    private String hashIp(String ipAddress) {
        return ipAddress == null || ipAddress.isBlank()
                ? null
                : SecretSupport.hmacSha256(properties.ipHashSalt(), ipAddress);
    }

    private String referrerDomain(String referrer) {
        if (referrer == null || referrer.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(referrer).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            // Analytics enrichment must not poison an otherwise valid click event.
            return null;
        }
    }

    private String normalizeCountry(String country) {
        return country == null || country.isBlank()
                ? "unknown"
                : country.trim().toUpperCase(Locale.ROOT);
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }
}
