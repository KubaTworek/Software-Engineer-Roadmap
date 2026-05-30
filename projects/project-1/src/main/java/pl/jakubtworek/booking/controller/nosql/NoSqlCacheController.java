package pl.jakubtworek.booking.controller.nosql;

import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.booking.dto.nosql.AvailabilitySnapshotResponse;
import pl.jakubtworek.booking.dto.nosql.EventCacheResponse;
import pl.jakubtworek.booking.dto.nosql.RateLimitResponse;
import pl.jakubtworek.booking.dto.nosql.ReservationHoldResponse;
import pl.jakubtworek.booking.service.NoSqlCacheService;

import java.util.UUID;

@RestController
@RequestMapping("/api/nosql/cache")
public class NoSqlCacheController {
    private final NoSqlCacheService noSqlCacheService;

    public NoSqlCacheController(NoSqlCacheService noSqlCacheService) {
        this.noSqlCacheService = noSqlCacheService;
    }

    @GetMapping("/events/{eventId}")
    public EventCacheResponse getEventDetails(@PathVariable UUID eventId) {
        return noSqlCacheService.getEventDetails(eventId);
    }

    @GetMapping("/events/{eventId}/availability")
    public AvailabilitySnapshotResponse getAvailabilitySnapshot(@PathVariable UUID eventId) {
        return noSqlCacheService.getAvailabilitySnapshot(eventId);
    }

    @DeleteMapping("/events/{eventId}")
    public void evictEventCaches(@PathVariable UUID eventId) {
        noSqlCacheService.evictEventCaches(eventId);
    }

    @PostMapping("/reservation-holds")
    public ReservationHoldResponse createReservationHold(@RequestParam UUID eventId,
                                                         @RequestParam String customerEmail) {
        return noSqlCacheService.createTemporaryHold(eventId, customerEmail);
    }

    @GetMapping("/reservation-holds/{holdId}")
    public ReservationHoldResponse getReservationHold(@PathVariable UUID holdId) {
        return noSqlCacheService.getTemporaryHold(holdId);
    }

    @PostMapping("/rate-limit/{clientKey}")
    public RateLimitResponse consumeRateLimitToken(@PathVariable String clientKey) {
        return noSqlCacheService.consumeRateLimitToken(clientKey);
    }
}
