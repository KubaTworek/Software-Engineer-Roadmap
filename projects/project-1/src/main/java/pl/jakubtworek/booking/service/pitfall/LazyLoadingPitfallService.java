package pl.jakubtworek.booking.service.pitfall;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.SpringPitfallReservationView;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.util.UUID;

/**
 * Serwis edukacyjny pokazujący problem lazy loadingu w JPA.
 *
 * Główna idea:
 *
 * - Reservation ma relacje lazy do Event i Customer.
 * - Jeśli pobierzesz Reservation bez dociągnięcia relacji,
 *   a potem spróbujesz użyć reservation.getEvent() poza transakcją,
 *   Hibernate może rzucić LazyInitializationException.
 *
 * Ta klasa pokazuje:
 *
 * - wariant błędny: encja odłączona z lazy relacjami,
 * - poprawkę przez mapowanie DTO wewnątrz transakcji,
 * - poprawkę przez fetch join,
 * - poprawkę przez EntityGraph,
 * - poprawkę przez DTO projection.
 */
@Service
public class LazyLoadingPitfallService {

    /**
     * Repozytorium rezerwacji.
     *
     * Zawiera różne metody pobierania:
     * - zwykłe findById,
     * - fetch join,
     * - EntityGraph,
     * - DTO projection.
     */
    private final ReservationRepository reservationRepository;

    /**
     * Constructor injection.
     */
    public LazyLoadingPitfallService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    /**
     * Celowo błędny wariant.
     *
     * Pobiera Reservation zwykłym findById(...), bez fetch join i bez EntityGraph.
     *
     * Metoda nie ma @Transactional, więc po zakończeniu metody encja może być
     * odłączona od persistence contextu.
     *
     * Jeśli kontroler później wywoła:
     *
     * reservation.getEvent().getName()
     * reservation.getCustomer().getEmail()
     *
     * może wystąpić LazyInitializationException.
     *
     * Ten wariant istnieje po to, żeby świadomie pokazać problem, nie po to,
     * żeby używać go produkcyjnie.
     */
    public Reservation loadDetachedReservationWithLazyRelations(UUID reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
    }

    /**
     * Poprawka nr 1: mapowanie DTO wewnątrz transakcji.
     *
     * Metoda jest oznaczona @Transactional(readOnly = true), więc podczas mapowania
     * działa persistence context.
     *
     * Jeśli relacje lazy zostaną dotknięte w toView(...), Hibernate ma jeszcze
     * aktywny kontekst i może dociągnąć brakujące dane.
     *
     * To działa, ale może ukrywać problem N+1, jeśli mapujesz wiele encji.
     */
    @Transactional(readOnly = true)
    public SpringPitfallReservationView mapInsideTransaction(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));

        return toView(reservation);
    }

    /**
     * Poprawka nr 2: fetch join.
     *
     * Repozytorium wykonuje zapytanie, które od razu pobiera Reservation razem
     * z wymaganymi relacjami, np. Event i Customer.
     *
     * Dzięki temu późniejsze mapowanie DTO nie musi odpalać dodatkowych lazy selectów.
     */
    @Transactional(readOnly = true)
    public SpringPitfallReservationView loadUsingFetchJoin(UUID reservationId) {
        Reservation reservation = reservationRepository.findByIdUsingFetchJoin(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));

        return toView(reservation);
    }

    /**
     * Poprawka nr 3: EntityGraph.
     *
     * Metoda repozytorium findDetailedById(...) ma @EntityGraph(attributePaths = {"event", "customer"}).
     *
     * To mówi Hibernate:
     * dla tego konkretnego odczytu dociągnij także event i customer.
     *
     * Efekt podobny do fetch join, ale konfiguracja grafu pobierania jest
     * oddzielona od JPQL.
     */
    @Transactional(readOnly = true)
    public SpringPitfallReservationView loadUsingEntityGraph(UUID reservationId) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));

        return toView(reservation);
    }

    /**
     * Poprawka nr 4: DTO projection.
     *
     * Repozytorium nie zwraca encji Reservation.
     * Zwraca od razu SpringPitfallReservationView zbudowany przez zapytanie JPQL.
     *
     * Zalety:
     * - brak lazy loadingu,
     * - pobierasz tylko potrzebne pola,
     * - nie wynosisz encji JPA poza warstwę persystencji,
     * - często najlepsze rozwiązanie dla endpointów read-only.
     */
    @Transactional(readOnly = true)
    public SpringPitfallReservationView loadUsingDtoProjection(UUID reservationId) {
        return reservationRepository.findProjectionById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
    }

    /**
     * Mapuje Reservation na DTO używane w endpointach Spring pitfalls.
     *
     * Ta metoda celowo dotyka relacji lazy:
     *
     * - reservation.getEvent().getId(),
     * - reservation.getEvent().getName(),
     * - reservation.getCustomer().getId(),
     * - reservation.getCustomer().getEmail().
     *
     * Dzięki temu dobrze widać różnicę między:
     *
     * - encją odłączoną od persistence contextu,
     * - mapowaniem wewnątrz transakcji,
     * - fetch join,
     * - EntityGraph.
     */
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