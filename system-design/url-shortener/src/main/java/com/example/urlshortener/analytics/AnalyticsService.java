package com.example.urlshortener.analytics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final ClickEventRepository clickEventRepository;
    private final DailyUrlStatsRepository dailyUrlStatsRepository;
    private final ClickCounterService clickCounterService;
    private final AnalyticsProperties properties;

    public AnalyticsService(
        ClickEventRepository clickEventRepository,
        DailyUrlStatsRepository dailyUrlStatsRepository,
        ClickCounterService clickCounterService,
        AnalyticsProperties properties
    ) {
        this.clickEventRepository = clickEventRepository;
        this.dailyUrlStatsRepository = dailyUrlStatsRepository;
        this.clickCounterService = clickCounterService;
        this.properties = properties;
    }

    @Async("analyticsExecutor")
    @EventListener
    @Transactional
    public void handleClick(ClickTrackedEvent event) {
        if (!properties.enabled()) return;

        try {
            LocalDate date = event.clickedAt().atZone(ZoneOffset.UTC).toLocalDate();
            String ipHash = hashIp(event.ipAddress());

            clickEventRepository.save(new ClickEvent(
                event.shortCode(),
                event.clickedAt(),
                ipHash,
                truncate(event.userAgent(), 2048),
                truncate(event.referrer(), 2048)
            ));

            DailyUrlStatsId statsId = new DailyUrlStatsId(event.shortCode(), date);
            DailyUrlStats stats = dailyUrlStatsRepository.findById(statsId)
                .orElseGet(() -> new DailyUrlStats(event.shortCode(), date, 0));
            stats.increment(1);
            dailyUrlStatsRepository.save(stats);

            clickCounterService.incrementTotal(event.shortCode());
            clickCounterService.incrementDaily(event.shortCode(), date);
        } catch (Exception exception) {
            log.warn("Failed to persist click analytics for shortCode={}", event.shortCode(), exception);
        }
    }

    private String hashIp(String ipAddress) throws Exception {
        if (ipAddress == null || ipAddress.isBlank()) return null;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest((properties.ipHashSalt() + ":" + ipAddress).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
