package pl.jakubtworek.backend.reservation.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.backend.reservation.application.ReservationService;

import java.util.UUID;

@RestController
@RequestMapping("/reservations")
public class ReservationController {
    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    ReservationResponse create(@Valid @RequestBody CreateReservationRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    ReservationResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/confirm")
    ReservationResponse confirm(@PathVariable UUID id) {
        return service.confirm(id);
    }

    @DeleteMapping("/{id}")
    ReservationResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
