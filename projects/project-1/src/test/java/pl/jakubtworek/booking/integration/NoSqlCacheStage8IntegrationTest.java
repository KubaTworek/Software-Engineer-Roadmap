package pl.jakubtworek.booking.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.dto.nosql.AvailabilitySnapshotResponse;
import pl.jakubtworek.booking.dto.nosql.EventCacheResponse;
import pl.jakubtworek.booking.dto.nosql.RateLimitResponse;
import pl.jakubtworek.booking.dto.nosql.ReservationHoldResponse;
import pl.jakubtworek.booking.entity.CapacityPool;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;
import pl.jakubtworek.booking.repository.EventRepository;
import pl.jakubtworek.booking.service.NoSqlCacheService;
import pl.jakubtworek.booking.service.ReservationService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NoSqlCacheStage8IntegrationTest {
    @Autowired NoSqlCacheService noSqlCacheService;
    @Autowired ReservationService reservationService;
    @Autowired EventRepository eventRepository;
    @Autowired CapacityPoolRepository capacityPoolRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private Event event;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        event = eventRepository.save(new Event(
                "Redis Cache Event",
                "Warsaw",
                "music",
                OffsetDateTime.of(2026, 9, 1, 20, 0, 0, 0, ZoneOffset.UTC)
        ));
        capacityPoolRepository.save(new CapacityPool(event, 10));
    }

    @Test
    void eventDetailsAreLoadedFromSqlThenReturnedFromCache() {
        EventCacheResponse first = noSqlCacheService.getEventDetails(event.getId());
        EventCacheResponse second = noSqlCacheService.getEventDetails(event.getId());

        assertThat(first.source()).isEqualTo("SQL");
        assertThat(second.source()).isEqualTo("CACHE");
        assertThat(second.eventId()).isEqualTo(event.getId());
        assertThat(second.availableCapacity()).isEqualTo(10);
    }

    @Test
    void availabilitySnapshotIsEventuallyConsistentUntilCacheIsEvictedByReservationFlow() {
        AvailabilitySnapshotResponse beforeReservation = noSqlCacheService.getAvailabilitySnapshot(event.getId());
        assertThat(beforeReservation.source()).isEqualTo("SQL");
        assertThat(beforeReservation.availableCapacity()).isEqualTo(10);

        reservationService.create(event.getId(), new ReservationCreateRequest("Cache Customer", "cache@example.com"));

        AvailabilitySnapshotResponse afterReservation = noSqlCacheService.getAvailabilitySnapshot(event.getId());
        assertThat(afterReservation.source()).isEqualTo("SQL");
        assertThat(afterReservation.availableCapacity()).isEqualTo(9);
    }

    @Test
    void reservationHoldUsesTtlStyleKeyValueStorage() {
        ReservationHoldResponse hold = noSqlCacheService.createTemporaryHold(event.getId(), "hold@example.com");
        ReservationHoldResponse loaded = noSqlCacheService.getTemporaryHold(hold.holdId());

        assertThat(loaded.holdId()).isEqualTo(hold.holdId());
        assertThat(loaded.eventId()).isEqualTo(event.getId());
        assertThat(loaded.customerEmail()).isEqualTo("hold@example.com");
        assertThat(loaded.active()).isTrue();
        assertThat(loaded.expiresAt()).isAfter(loaded.createdAt());
    }

    @Test
    void rateLimiterConsumesTokensAndRejectsAfterLimit() {
        String clientKey = "stage8-client";

        RateLimitResponse first = noSqlCacheService.consumeRateLimitToken(clientKey);
        RateLimitResponse fifth = null;
        for (int i = 0; i < 4; i++) {
            fifth = noSqlCacheService.consumeRateLimitToken(clientKey);
        }
        RateLimitResponse sixth = noSqlCacheService.consumeRateLimitToken(clientKey);

        assertThat(first.allowed()).isTrue();
        assertThat(first.remainingTokens()).isEqualTo(4);
        assertThat(fifth).isNotNull();
        assertThat(fifth.allowed()).isTrue();
        assertThat(fifth.remainingTokens()).isEqualTo(0);
        assertThat(sixth.allowed()).isFalse();
        assertThat(sixth.remainingTokens()).isEqualTo(0);
    }

    private void cleanDatabase() {
        jdbcTemplate.update("delete from audit_logs");
        jdbcTemplate.update("delete from outbound_messages");
        jdbcTemplate.update("delete from reservations");
        jdbcTemplate.update("delete from capacity_pools");
        jdbcTemplate.update("delete from app_users");
        jdbcTemplate.update("delete from events");
        jdbcTemplate.update("delete from customers");
        jdbcTemplate.update("delete from organizations");
    }
}
