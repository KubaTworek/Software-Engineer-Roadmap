package com.example.urlshortener.analytics;

import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.model.UrlStatus;
import com.example.urlshortener.repository.ShortUrlRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;
    private final DailyUrlStatsRepository dailyUrlStatsRepository;
    private final ClickCounterService clickCounterService;
    private final Clock clock;

    public DashboardService(
        ShortUrlRepository shortUrlRepository,
        ClickEventRepository clickEventRepository,
        DailyUrlStatsRepository dailyUrlStatsRepository,
        ClickCounterService clickCounterService,
        Clock clock
    ) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
        this.dailyUrlStatsRepository = dailyUrlStatsRepository;
        this.clickCounterService = clickCounterService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UrlAnalyticsResponse analyticsFor(String shortCode, LocalDate from, LocalDate to) {
        if (!shortUrlRepository.existsByShortCode(shortCode)) {
            throw new ShortUrlNotFoundException(shortCode);
        }

        LocalDate safeTo = to == null ? LocalDate.now(clock) : to;
        LocalDate safeFrom = from == null ? safeTo.minusDays(6) : from;
        var fromInstant = safeFrom.atStartOfDay().toInstant(ZoneOffset.UTC);
        var toInstant = safeTo.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC);
        var topLimit = PageRequest.of(0, 10);

        Map<LocalDate, DailyUrlStats> statsByDate = dailyUrlStatsRepository
            .findByShortCodeAndDateBetweenOrderByDateAsc(shortCode, safeFrom, safeTo)
            .stream()
            .collect(Collectors.toMap(DailyUrlStats::getDate, Function.identity()));

        var daily = safeFrom.datesUntil(safeTo.plusDays(1))
            .map(date -> new DailyClicksResponse(
                date,
                clickCounterService.getDaily(shortCode, date)
                    .orElseGet(() -> statsByDate.getOrDefault(date, new DailyUrlStats(shortCode, date, 0)).getClicks())
            ))
            .toList();

        long totalClicks = clickCounterService.getTotal(shortCode)
            .orElseGet(() -> clickEventRepository.countByShortCode(shortCode));
        long suspiciousClicks = clickEventRepository.countByShortCodeAndSuspiciousTrue(shortCode);

        return new UrlAnalyticsResponse(
            shortCode,
            totalClicks,
            suspiciousClicks,
            safeFrom,
            safeTo,
            daily,
            clickEventRepository.topCountries(shortCode, fromInstant, toInstant, topLimit),
            clickEventRepository.topDevices(shortCode, fromInstant, toInstant, topLimit),
            clickEventRepository.topBrowsers(shortCode, fromInstant, toInstant, topLimit),
            clickEventRepository.topReferrers(shortCode, fromInstant, toInstant, topLimit)
        );
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        LocalDate today = LocalDate.now(clock);
        long totalUrls = shortUrlRepository.count();
        long activeUrls = shortUrlRepository.countByStatus(UrlStatus.ACTIVE);
        long blockedUrls = shortUrlRepository.countByStatus(UrlStatus.BLOCKED);
        long totalClicks = clickEventRepository.count();
        long suspiciousClicks = clickEventRepository.countBySuspiciousTrue();
        long clicksToday = dailyUrlStatsRepository.findAll().stream()
            .filter(stats -> stats.getDate().equals(today))
            .mapToLong(DailyUrlStats::getClicks)
            .sum();

        return new DashboardSummaryResponse(totalUrls, activeUrls, blockedUrls, totalClicks, suspiciousClicks, clicksToday, today);
    }
}
