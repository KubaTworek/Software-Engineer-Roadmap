package pl.jakubtworek.booking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.entity.Customer;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.exception.CapacityUnavailableException;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;
import pl.jakubtworek.booking.repository.CustomerRepository;
import pl.jakubtworek.booking.repository.EventRepository;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.util.UUID;

@Service
public class ReservationService {
    private final EventRepository eventRepository;
    private final CustomerRepository customerRepository;
    private final ReservationRepository reservationRepository;
    private final CapacityPoolRepository capacityPoolRepository;

    public ReservationService(
            EventRepository eventRepository,
            CustomerRepository customerRepository,
            ReservationRepository reservationRepository,
            CapacityPoolRepository capacityPoolRepository
    ) {
        this.eventRepository = eventRepository;
        this.customerRepository = customerRepository;
        this.reservationRepository = reservationRepository;
        this.capacityPoolRepository = capacityPoolRepository;
    }

    @Transactional
    public ReservationResponse create(UUID eventId, ReservationCreateRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));

        int updatedRows = capacityPoolRepository.reserveOneSeatIfAvailable(eventId);
        if (updatedRows != 1) {
            throw new CapacityUnavailableException("No available capacity for event: " + eventId);
        }

        Customer customer = customerRepository.findByEmail(request.customerEmail())
                .orElseGet(() -> customerRepository.save(new Customer(request.customerFullName(), request.customerEmail())));

        Reservation reservation = reservationRepository.save(new Reservation(event, customer));
        return get(reservation.getId());
    }

    @Transactional
    public ReservationResponse confirm(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
        reservation.confirm();
        return get(reservationId);
    }

    @Transactional
    public ReservationResponse cancel(UUID reservationId) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
        boolean cancelledNow = reservation.cancel();
        if (cancelledNow) {
            capacityPoolRepository.releaseOneSeat(reservation.getEvent().getId());
        }
        return get(reservationId);
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
