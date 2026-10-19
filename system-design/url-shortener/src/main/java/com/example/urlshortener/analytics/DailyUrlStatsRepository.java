package com.example.urlshortener.analytics;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyUrlStatsRepository extends JpaRepository<DailyUrlStats, DailyUrlStatsId> {
    List<DailyUrlStats> findByShortCodeAndDateBetweenOrderByDateAsc(String shortCode, LocalDate from, LocalDate to);

    @Query("select coalesce(sum(stats.clicks), 0) from DailyUrlStats stats where stats.id.date = :date")
    long sumClicksByDate(@Param("date") LocalDate date);

}
