package pl.jakubtworek.booking.controller.performance;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.booking.dto.EventStatsResponse;
import pl.jakubtworek.booking.dto.ExplainAnalyzeResponse;
import pl.jakubtworek.booking.dto.KeysetReservationPageResponse;
import pl.jakubtworek.booking.dto.ReservationListItemResponse;
import pl.jakubtworek.booking.entity.ReservationStatus;
import pl.jakubtworek.booking.service.SqlPerformanceService;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class SqlPerformanceController {
    private final SqlPerformanceService sqlPerformanceService;

    public SqlPerformanceController(SqlPerformanceService sqlPerformanceService) {
        this.sqlPerformanceService = sqlPerformanceService;
    }

    @GetMapping("/api/organizations/{organizationId}/reservations")
    public Page<ReservationListItemResponse> organizationReservations(
            @PathVariable UUID organizationId,
            @RequestParam ReservationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return sqlPerformanceService.organizationReservations(organizationId, status, page, size);
    }

    @GetMapping("/api/customers/{customerId}/reservations")
    public Page<ReservationListItemResponse> customerReservationsOffset(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return sqlPerformanceService.customerReservationsOffset(customerId, page, size);
    }

    @GetMapping("/api/customers/{customerId}/reservations/keyset")
    public KeysetReservationPageResponse customerReservationsKeyset(
            @PathVariable UUID customerId,
            @RequestParam(required = false) Instant afterCreatedAt,
            @RequestParam(required = false) UUID afterId,
            @RequestParam(defaultValue = "50") int size
    ) {
        return sqlPerformanceService.customerReservationsKeyset(customerId, afterCreatedAt, size);
    }

    @GetMapping("/api/events/{eventId}/stats")
    public EventStatsResponse eventStats(@PathVariable UUID eventId) {
        return sqlPerformanceService.eventStats(eventId);
    }

    @GetMapping("/api/events/{eventId}/reservations/n-plus-one")
    public List<ReservationListItemResponse> eventReservationsNaiveNPlusOne(@PathVariable UUID eventId) {
        return sqlPerformanceService.eventReservationsNaiveNPlusOne(eventId);
    }

    @GetMapping("/api/events/{eventId}/reservations/fetch-join")
    public List<ReservationListItemResponse> eventReservationsFetchJoin(@PathVariable UUID eventId) {
        return sqlPerformanceService.eventReservationsFetchJoin(eventId);
    }

    @GetMapping("/api/events/{eventId}/reservations/entity-graph")
    public List<ReservationListItemResponse> eventReservationsEntityGraph(@PathVariable UUID eventId) {
        return sqlPerformanceService.eventReservationsEntityGraph(eventId);
    }

    @GetMapping("/api/performance/explain/event-search")
    public ExplainAnalyzeResponse explainEventSearch(
            @RequestParam String city,
            @RequestParam String category,
            @RequestParam("from") OffsetDateTime from
    ) {
        return sqlPerformanceService.explainEventSearch(city, category, from);
    }
}
