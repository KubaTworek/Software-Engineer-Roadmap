package com.example.urlshortener.analytics;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {
    long countByShortCode(String shortCode);
    boolean existsByEventId(String eventId);
    long countByShortCodeAndSuspiciousTrue(String shortCode);
    long countBySuspiciousTrue();

    @Query("""
        select new com.example.urlshortener.analytics.DimensionCount(coalesce(c.country, 'unknown'), count(c))
        from ClickEvent c
        where c.shortCode = :shortCode and c.clickedAt between :from and :to
        group by coalesce(c.country, 'unknown')
        order by count(c) desc
    """)
    List<DimensionCount> topCountries(@Param("shortCode") String shortCode, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("""
        select new com.example.urlshortener.analytics.DimensionCount(coalesce(c.deviceType, 'unknown'), count(c))
        from ClickEvent c
        where c.shortCode = :shortCode and c.clickedAt between :from and :to
        group by coalesce(c.deviceType, 'unknown')
        order by count(c) desc
    """)
    List<DimensionCount> topDevices(@Param("shortCode") String shortCode, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("""
        select new com.example.urlshortener.analytics.DimensionCount(coalesce(c.browser, 'unknown'), count(c))
        from ClickEvent c
        where c.shortCode = :shortCode and c.clickedAt between :from and :to
        group by coalesce(c.browser, 'unknown')
        order by count(c) desc
    """)
    List<DimensionCount> topBrowsers(@Param("shortCode") String shortCode, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("""
        select new com.example.urlshortener.analytics.DimensionCount(coalesce(c.referrerDomain, 'direct'), count(c))
        from ClickEvent c
        where c.shortCode = :shortCode and c.clickedAt between :from and :to
        group by coalesce(c.referrerDomain, 'direct')
        order by count(c) desc
    """)
    List<DimensionCount> topReferrers(@Param("shortCode") String shortCode, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
