package pl.jakubtworek.booking.service.async;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.exception.BusinessRuleException;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.util.UUID;

@Service
public class ReservationStatusService {
    private final ReservationRepository reservationRepository;

    public ReservationStatusService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public ReservationResponse confirmAfterPayment(UUID reservationId) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
        try {
            reservation.confirm();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException(e.getMessage());
        }
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse markPaymentTimeout(UUID reservationId) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
        try {
            reservation.markPaymentTimeout();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException(e.getMessage());
        }
        return toResponse(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(UUID reservationId) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
        return toResponse(reservation);
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEvent().getId(),
                reservation.getEvent().getName(),
                reservation.getCustomer().getId(),
                reservation.getCustomer().getEmail(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getConfirmedAt(),
                reservation.getCancelledAt()
        );
    }
}
