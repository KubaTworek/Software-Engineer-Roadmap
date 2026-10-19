package com.example.urlshortener.analytics;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Executes the two SQL operations whose concurrency semantics matter for an
 * at-least-once consumer. PostgreSQL is the production contract. The H2 branch
 * exists only so fast tests can exercise orchestration without pretending to
 * prove PostgreSQL locking or upsert behaviour.
 */
@Repository
public class ClickAnalyticsWriter {
    private final JdbcTemplate jdbc;
    private final boolean h2;

    public ClickAnalyticsWriter(JdbcTemplate jdbc, DataSourceProperties properties) {
        this.jdbc = jdbc;
        this.h2 = properties.determineUrl().startsWith("jdbc:h2:");
    }

    public boolean insertIfAbsent(ClickEvent event) {
        String columns = """
                event_id, short_code, clicked_at, ip_hash, user_agent, referrer,
                referrer_domain, country, device_type, browser, suspicious, abuse_reason
                """;
        String placeholders = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?";
        String sql = h2
                ? "INSERT INTO click_events (" + columns + ") SELECT " + placeholders
                        + " WHERE NOT EXISTS (SELECT 1 FROM click_events WHERE event_id = ?)"
                : "INSERT INTO click_events (" + columns + ") VALUES (" + placeholders
                        + ") ON CONFLICT (event_id) DO NOTHING";
        Object[] values = {
                event.getEventId(), event.getShortCode(), Timestamp.from(event.getClickedAt()),
                event.getIpHash(), event.getUserAgent(), event.getReferrer(), event.getReferrerDomain(),
                event.getCountry(), event.getDeviceType(), event.getBrowser(), event.isSuspicious(),
                event.getAbuseReason()
        };
        if (!h2) {
            return jdbc.update(sql, values) == 1;
        }
        Object[] h2Values = java.util.Arrays.copyOf(values, values.length + 1);
        h2Values[h2Values.length - 1] = event.getEventId();
        return jdbc.update(sql, h2Values) == 1;
    }

    public void incrementDaily(String shortCode, LocalDate date) {
        if (!h2) {
            jdbc.update("""
                    INSERT INTO url_daily_stats (short_code, stats_date, clicks)
                    VALUES (?, ?, 1)
                    ON CONFLICT (short_code, stats_date)
                    DO UPDATE SET clicks = url_daily_stats.clicks + 1
                    """, shortCode, Date.valueOf(date));
            return;
        }
        int updated = jdbc.update(
                "UPDATE url_daily_stats SET clicks = clicks + 1 WHERE short_code = ? AND stats_date = ?",
                shortCode, Date.valueOf(date));
        if (updated == 0) {
            jdbc.update("INSERT INTO url_daily_stats (short_code, stats_date, clicks) VALUES (?, ?, 1)",
                    shortCode, Date.valueOf(date));
        }
    }
}
