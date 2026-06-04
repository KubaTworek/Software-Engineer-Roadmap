package pl.jakubtworek.backend.reservation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.backend.reservation.api.CreateReservationRequest;
import pl.jakubtworek.backend.reservation.api.ReservationResponse;
import pl.jakubtworek.backend.reservation.client.CatalogClient;
import pl.jakubtworek.backend.reservation.domain.ReservationEntity;
import pl.jakubtworek.backend.reservation.repository.ReservationRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class ReservationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private final ReservationRepository repository;
    private final CatalogClient catalogClient;

    public ReservationService(ReservationRepository repository, CatalogClient catalogClient) {
        this.repository = repository;
        this.catalogClient = catalogClient;
    }

    @Transactional
    public ReservationResponse create(CreateReservationRequest request) {
        CatalogClient.AvailabilityResponse availability = catalogClient.getAvailability(request.eventId());
        if (availability == null) {
            throw new IllegalStateException("Catalog service returned empty availability response");
        }
        if (availability.availableTickets() < request.quantity()) {
            throw new IllegalArgumentException("Not enough tickets available for event " + request.eventId());
        }

        ReservationEntity reservation = ReservationEntity.pending(request.eventId(), request.userId(), request.quantity());
        return toResponse(repository.save(reservation));
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(UUID id) {
        ReservationEntity reservation = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + id));
        if (reservation.isExpired(Instant.now())) {
            throw new IllegalStateException("Reservation expired: " + id);
        }
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse confirm(UUID id) {
        ReservationEntity reservation = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + id));
        if (reservation.isExpired(Instant.now())) {
            reservation.expire();
            throw new IllegalStateException("Reservation expired: " + id);
        }
        reservation.confirm();
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse cancel(UUID id) {
        ReservationEntity reservation = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + id));
        reservation.cancel();
        return toResponse(reservation);
    }

    private ReservationResponse toResponse(ReservationEntity reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEventId(),
                reservation.getUserId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }
}
