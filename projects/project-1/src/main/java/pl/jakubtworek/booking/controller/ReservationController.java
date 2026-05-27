package pl.jakubtworek.booking.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.service.ReservationService;
import pl.jakubtworek.booking.service.async.AsyncReservationService;
import pl.jakubtworek.booking.service.async.PaymentScenario;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ReservationController {
    private final ReservationService reservationService;
    private final AsyncReservationService asyncReservationService;

    public ReservationController(ReservationService reservationService, AsyncReservationService asyncReservationService) {
        this.reservationService = reservationService;
        this.asyncReservationService = asyncReservationService;
    }

    @PostMapping("/events/{eventId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@PathVariable UUID eventId, @Valid @RequestBody ReservationCreateRequest request) {
        return reservationService.create(eventId, request);
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    public ReservationResponse confirm(@PathVariable UUID reservationId) {
        return reservationService.confirm(reservationId);
    }

    @PostMapping("/reservations/{reservationId}/confirm-async")
    public ReservationResponse confirmAsync(
            @PathVariable UUID reservationId,
            @RequestParam(defaultValue = "APPROVED") PaymentScenario paymentScenario
    ) {
        return asyncReservationService.confirm(reservationId, paymentScenario).join();
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ReservationResponse cancel(@PathVariable UUID reservationId) {
        return reservationService.cancel(reservationId);
    }

    @GetMapping("/reservations/{reservationId}")
    public ReservationResponse get(@PathVariable UUID reservationId) {
        return reservationService.get(reservationId);
    }
}
