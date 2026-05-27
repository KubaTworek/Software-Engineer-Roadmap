package pl.jakubtworek.booking.service.concurrency;

import org.springframework.stereotype.Component;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.entity.Customer;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.CustomerRepository;
import pl.jakubtworek.booking.repository.EventRepository;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.util.UUID;

@Component
class ReservationCreationSupport {
    private final EventRepository eventRepository;
    private final CustomerRepository customerRepository;
    private final ReservationRepository reservationRepository;

    ReservationCreationSupport(
            EventRepository eventRepository,
            CustomerRepository customerRepository,
            ReservationRepository reservationRepository
    ) {
        this.eventRepository = eventRepository;
        this.customerRepository = customerRepository;
        this.reservationRepository = reservationRepository;
    }

    Event getEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
    }

    Reservation saveReservation(Event event, ReservationCreateRequest request) {
        Customer customer = customerRepository.findByEmail(request.customerEmail())
                .orElseGet(() -> customerRepository.save(new Customer(request.customerFullName(), request.customerEmail())));
        return reservationRepository.save(new Reservation(event, customer));
    }
}
