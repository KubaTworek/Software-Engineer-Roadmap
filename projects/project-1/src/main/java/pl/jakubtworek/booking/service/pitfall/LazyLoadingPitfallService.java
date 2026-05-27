package pl.jakubtworek.booking.service.pitfall;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.SpringPitfallReservationView;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.util.UUID;

@Service
public class LazyLoadingPitfallService {
    private final ReservationRepository reservationRepository;

    public LazyLoadingPitfallService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    public Reservation loadDetachedReservationWithLazyRelations(UUID reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
    }

    @Transactional(readOnly = true)
    public SpringPitfallReservationView mapInsideTransaction(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
        return toView(reservation);
    }

    @Transactional(readOnly = true)
    public SpringPitfallReservationView loadUsingFetchJoin(UUID reservationId) {
        Reservation reservation = reservationRepository.findByIdUsingFetchJoin(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
        return toView(reservation);
    }

    @Transactional(readOnly = true)
    public SpringPitfallReservationView loadUsingEntityGraph(UUID reservationId) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
        return toView(reservation);
    }

    @Transactional(readOnly = true)
    public SpringPitfallReservationView loadUsingDtoProjection(UUID reservationId) {
        return reservationRepository.findProjectionById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
    }

    public SpringPitfallReservationView toView(Reservation reservation) {
        return new SpringPitfallReservationView(
                reservation.getId(),
                reservation.getEvent().getId(),
                reservation.getEvent().getName(),
                reservation.getCustomer().getId(),
                reservation.getCustomer().getEmail(),
                reservation.getStatus()
        );
    }
}
