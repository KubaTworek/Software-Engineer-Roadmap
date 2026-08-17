package com.example.urlshortener.analytics;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class DailyUrlStatsId implements Serializable {
    private String shortCode;
    private LocalDate date;

    public DailyUrlStatsId() {}

    public DailyUrlStatsId(String shortCode, LocalDate date) {
        this.shortCode = shortCode;
        this.date = date;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof DailyUrlStatsId that)) return false;
        return Objects.equals(shortCode, that.shortCode) && Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shortCode, date);
    }
}
