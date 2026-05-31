package pl.jakubtworek.booking.integration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.EventStatsResponse;
import pl.jakubtworek.booking.dto.KeysetReservationPageResponse;
import pl.jakubtworek.booking.dto.ReservationListItemResponse;
import pl.jakubtworek.booking.entity.*;
import pl.jakubtworek.booking.repository.*;
import pl.jakubtworek.booking.service.SqlPerformanceService;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SqlPerformanceStage5IntegrationTest {
    @Autowired SqlPerformanceService sqlPerformanceService;
    @Autowired EventRepository eventRepository;
    @Autowired CapacityPoolRepository capacityPoolRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private Organization organization;
    private Event warsawMusicEvent;
    private Event krakowTechEvent;
    private Customer customer;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        organization = organizationRepository.save(new Organization("ACME Events"));
        customer = customerRepository.save(new Customer("Anna Customer", "anna.stage5@example.com"));

        warsawMusicEvent = eventRepository.save(new Event(
                organization,
                "Warsaw Music Night",
                "Warsaw",
                "music",
                OffsetDateTime.of(2026, 6, 10, 20, 0, 0, 0, ZoneOffset.UTC)
        ));
        krakowTechEvent = eventRepository.save(new Event(
                organization,
                "Krakow Tech Summit",
                "Krakow",
                "tech",
                OffsetDateTime.of(2026, 6, 12, 10, 0, 0, 0, ZoneOffset.UTC)
        ));
        capacityPoolRepository.save(new CapacityPool(warsawMusicEvent, 100));
        capacityPoolRepository.save(new CapacityPool(krakowTechEvent, 100));
    }

    @Test
    void searchesEventsByCityCategoryAndStartDate() {
        // given & when
        var results = sqlPerformanceService.searchEvents(
                "Warsaw",
                OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                "music"
        );

        // then
        assertThat(results)
                .extracting(pl.jakubtworek.booking.dto.EventSearchResponse::id)
                .containsExactly(warsawMusicEvent.getId());
    }

    @Test
    void returnsOrganizationReservationsByStatusUsingOffsetPagination() {
        // given
        Reservation confirmed = reservationRepository.save(new Reservation(warsawMusicEvent, customer));
        confirmed.confirm();
        reservationRepository.save(confirmed);
        reservationRepository.save(new Reservation(warsawMusicEvent, customer));

        // when
        var page = sqlPerformanceService.organizationReservations(
                organization.getId(),
                ReservationStatus.CONFIRMED,
                0,
                10
        );

        // then
        assertThat(page.getContent())
                .extracting(ReservationListItemResponse::id)
                .containsExactly(confirmed.getId());
    }

    @Test
    void comparesOffsetAndKeysetPaginationForCustomerReservations() {
        // given
        Reservation oldReservation = reservationRepository.save(new Reservation(warsawMusicEvent, customer));
        Reservation middleReservation = reservationRepository.save(new Reservation(warsawMusicEvent, customer));
        Reservation newestReservation = reservationRepository.save(new Reservation(krakowTechEvent, customer));
        reservationRepository.flush();

        setCreatedAt(oldReservation, "2026-06-01T10:00:00Z");
        setCreatedAt(middleReservation, "2026-06-02T10:00:00Z");
        setCreatedAt(newestReservation, "2026-06-03T10:00:00Z");

        // when & then
        var offsetPage = sqlPerformanceService.customerReservationsOffset(customer.getId(), 0, 2);
        assertThat(offsetPage.getContent())
                .extracting(ReservationListItemResponse::id)
                .containsExactly(newestReservation.getId(), middleReservation.getId());

        KeysetReservationPageResponse firstKeysetPage = sqlPerformanceService.customerReservationsKeyset(customer.getId(), null, 2);
        assertThat(firstKeysetPage.items())
                .extracting(ReservationListItemResponse::id)
                .containsExactly(newestReservation.getId(), middleReservation.getId());
        assertThat(firstKeysetPage.hasNext()).isTrue();

        KeysetReservationPageResponse secondKeysetPage = sqlPerformanceService.customerReservationsKeyset(
                customer.getId(),
                firstKeysetPage.nextAfterCreatedAt(),
                2
        );
        assertThat(secondKeysetPage.items())
                .extracting(ReservationListItemResponse::id)
                .containsExactly(oldReservation.getId());
        assertThat(secondKeysetPage.hasNext()).isFalse();
    }

    @Test
    void calculatesEventStatsGroupedByStatus() {
        // given
        Reservation pending = reservationRepository.save(new Reservation(warsawMusicEvent, customer));
        Reservation confirmed = new Reservation(warsawMusicEvent, customer);
        confirmed.confirm();
        reservationRepository.save(confirmed);
        Reservation cancelled = new Reservation(warsawMusicEvent, customer);
        cancelled.cancel();
        reservationRepository.save(cancelled);
        Reservation timeout = new Reservation(warsawMusicEvent, customer);
        timeout.markPaymentTimeout();
        reservationRepository.save(timeout);

        // when
        EventStatsResponse stats = sqlPerformanceService.eventStats(warsawMusicEvent.getId());

        // then
        assertThat(stats.totalReservations()).isEqualTo(4);
        assertThat(stats.pendingReservations()).isEqualTo(1);
        assertThat(stats.confirmedReservations()).isEqualTo(1);
        assertThat(stats.cancelledReservations()).isEqualTo(1);
        assertThat(stats.paymentTimeoutReservations()).isEqualTo(1);
        assertThat(pending.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void returnsSameDataForNaiveNPlusOneFetchJoinAndEntityGraphVariants() {
        // given
        Reservation first = reservationRepository.save(new Reservation(warsawMusicEvent, customer));
        Reservation second = reservationRepository.save(new Reservation(warsawMusicEvent, customer));

        // when
        List<ReservationListItemResponse> naive = sqlPerformanceService.eventReservationsNaiveNPlusOne(warsawMusicEvent.getId());
        List<ReservationListItemResponse> fetchJoin = sqlPerformanceService.eventReservationsFetchJoin(warsawMusicEvent.getId());
        List<ReservationListItemResponse> entityGraph = sqlPerformanceService.eventReservationsEntityGraph(warsawMusicEvent.getId());

        // then
        assertThat(naive).hasSize(2);
        assertThat(fetchJoin).hasSize(2);
        assertThat(entityGraph).hasSize(2);
        assertThat(fetchJoin).extracting(ReservationListItemResponse::id).containsExactlyElementsOf(
                naive.stream().map(ReservationListItemResponse::id).toList()
        );
        assertThat(entityGraph).extracting(ReservationListItemResponse::id).containsExactlyElementsOf(
                naive.stream().map(ReservationListItemResponse::id).toList()
        );
        assertThat(first.getEvent().getId()).isEqualTo(warsawMusicEvent.getId());
        assertThat(second.getCustomer().getId()).isEqualTo(customer.getId());
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

    private void setCreatedAt(Reservation reservation, String instant) {
        jdbcTemplate.update(
                "update reservations set created_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.parse(instant)),
                reservation.getId()
        );
    }
}
