package com.example.urlshortener.analytics;

import com.example.urlshortener.abuse.AbuseDetectionService;
import com.example.urlshortener.abuse.AbuseVerdict;
import com.example.urlshortener.queue.ClickMessage;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final ClickEventRepository clickEventRepository;
    private final DailyUrlStatsRepository dailyUrlStatsRepository;
    private final ClickCounterService clickCounterService;
    private final AnalyticsProperties properties;
    private final AbuseDetectionService abuseDetectionService;

    public AnalyticsService(
        ClickEventRepository clickEventRepository,
        DailyUrlStatsRepository dailyUrlStatsRepository,
        ClickCounterService clickCounterService,
        AnalyticsProperties properties,
        AbuseDetectionService abuseDetectionService
    ) {
        this.clickEventRepository = clickEventRepository;
        this.dailyUrlStatsRepository = dailyUrlStatsRepository;
        this.clickCounterService = clickCounterService;
        this.properties = properties;
        this.abuseDetectionService = abuseDetectionService;
    }

    @Transactional
    public void processClick(ClickMessage message) {
        if (!properties.enabled()) return;

        try {
            if (clickEventRepository.existsByEventId(message.eventId())) {
                log.debug("Skipping duplicate click eventId={}", message.eventId());
                return;
            }

            AbuseVerdict verdict = abuseDetectionService.evaluate(message);
            LocalDate date = message.clickedAt().atZone(ZoneOffset.UTC).toLocalDate();
            String ipHash = hashIp(message.ipAddress());

            clickEventRepository.save(new ClickEvent(
                message.eventId(),
                message.shortCode(),
                message.clickedAt(),
                ipHash,
                truncate(message.userAgent(), 2048),
                truncate(message.referrer(), 2048),
                truncate(referrerDomain(message.referrer()), 255),
                normalizeCountry(message.country()),
                verdict.deviceType(),
                verdict.browser(),
                verdict.suspicious(),
                truncate(verdict.reason(), 2048)
            ));

            DailyUrlStatsId statsId = new DailyUrlStatsId(message.shortCode(), date);
            DailyUrlStats stats = dailyUrlStatsRepository.findById(statsId)
                .orElseGet(() -> new DailyUrlStats(message.shortCode(), date, 0));
            stats.increment(1);
            dailyUrlStatsRepository.save(stats);

            clickCounterService.incrementTotal(message.shortCode());
            clickCounterService.incrementDaily(message.shortCode(), date);
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("Skipping duplicate click eventId={}", message.eventId());
        } catch (Exception exception) {
            // Let Rabbit listener send this message to DLQ when the failure is persistent.
            log.warn("Failed to persist queued click analytics eventId={} shortCode={}", message.eventId(), message.shortCode(), exception);
            throw new IllegalStateException(exception);
        }
    }

    private String hashIp(String ipAddress) throws Exception {
        if (ipAddress == null || ipAddress.isBlank()) return null;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest((properties.ipHashSalt() + ":" + ipAddress).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private String referrerDomain(String referrer) {
        if (referrer == null || referrer.isBlank()) return null;
        try {
            String host = URI.create(referrer).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeCountry(String country) {
        if (country == null || country.isBlank()) return "unknown";
        return country.trim().toUpperCase(Locale.ROOT);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
