package pl.jakubtworek.booking.service.concurrency;

import org.springframework.stereotype.Component;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.config.ReservationPolicyProperties;
import pl.jakubtworek.booking.service.CustomerRegistrationService;
import pl.jakubtworek.booking.entity.Customer;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.EventRepository;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.util.UUID;
import java.time.Clock;

@Component
class ReservationCreationSupport {
    private final EventRepository eventRepository;
    private final CustomerRegistrationService customerRegistrationService;
    private final ReservationRepository reservationRepository;
    private final Clock clock;
    private final ReservationPolicyProperties policy;

    ReservationCreationSupport(
            EventRepository eventRepository,
            CustomerRegistrationService customerRegistrationService,
            ReservationRepository reservationRepository,
            Clock clock,
            ReservationPolicyProperties policy
    ) {
        this.eventRepository = eventRepository;
        this.customerRegistrationService = customerRegistrationService;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
        this.policy = policy;
    }

    Event getEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
    }

    Reservation saveReservation(Event event, ReservationCreateRequest request) {
        Customer customer = customerRegistrationService.resolve(
                request.customerFullName(), request.customerEmail());
        /*
         * Wszystkie strategie współbieżności muszą tworzyć ten sam model domenowy.
         * Gdyby laboratorium optimistic/pessimistic tworzyło rezerwację innym
         * konstruktorem niż główny flow, porównywalibyśmy także różną semantykę
         * czasu, a nie wyłącznie mechanizm ochrony capacity.
         */
        return reservationRepository.save(new Reservation(
                event,
                customer,
                clock.instant(),
                policy.holdDuration()
        ));
    }
}
