package com.example.urlshortener.analytics;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "url_daily_stats")
@IdClass(DailyUrlStatsId.class)
public class DailyUrlStats {

    @Id
    @Column(name = "short_code", nullable = false, length = 32)
    private String shortCode;

    @Id
    @Column(name = "stats_date", nullable = false)
    private LocalDate date;

    @Column(name = "clicks", nullable = false)
    private long clicks;

    protected DailyUrlStats() {}

    public DailyUrlStats(String shortCode, LocalDate date, long clicks) {
        this.shortCode = shortCode;
        this.date = date;
        this.clicks = clicks;
    }

    public String getShortCode() { return shortCode; }
    public LocalDate getDate() { return date; }
    public long getClicks() { return clicks; }

    public void increment(long delta) {
        this.clicks += delta;
    }
}
