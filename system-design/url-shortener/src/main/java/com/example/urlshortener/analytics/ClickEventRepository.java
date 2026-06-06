package com.example.urlshortener.analytics;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {
    long countByShortCode(String shortCode);

    @Query("select count(e) from ClickEvent e where e.shortCode = :shortCode and e.clickedAt >= :from and e.clickedAt < :to")
    long countByShortCodeBetween(@Param("shortCode") String shortCode, @Param("from") Instant from, @Param("to") Instant to);
}
