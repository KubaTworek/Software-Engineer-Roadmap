package com.example.urlshortener.analytics;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyUrlStatsRepository extends JpaRepository<DailyUrlStats, DailyUrlStatsId> {
    List<DailyUrlStats> findByShortCodeAndDateBetweenOrderByDateAsc(String shortCode, LocalDate from, LocalDate to);
}
