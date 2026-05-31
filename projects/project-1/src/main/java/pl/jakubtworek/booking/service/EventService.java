package pl.jakubtworek.booking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.EventCreateRequest;
import pl.jakubtworek.booking.dto.EventResponse;
import pl.jakubtworek.booking.entity.CapacityPool;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;
import pl.jakubtworek.booking.repository.EventRepository;

import java.util.UUID;

/**
 * Serwis aplikacyjny odpowiedzialny za podstawowe operacje na eventach.
 *
 * W klasycznym monolicie warstwowym serwis jest miejscem, w którym powinna
 * znajdować się logika aplikacyjna:
 *
 * - tworzenie eventu,
 * - powiązanie eventu z pulą miejsc,
 * - pobieranie eventu razem z jego dostępnością,
 * - obsługa przypadków braku danych.
 *
 * Serwis nie powinien znać szczegółów HTTP. Dlatego nie zwraca ResponseEntity
 * i nie operuje na kodach statusu. Te szczegóły należą do warstwy kontrolera
 * oraz globalnego exception handlera.
 */
@Service
public class EventService {

    /**
     * Repozytorium JPA dla encji Event.
     *
     * Odpowiada za zapis i odczyt eventów z relacyjnej bazy danych.
     */
    private final EventRepository eventRepository;

    /**
     * Repozytorium JPA dla encji CapacityPool.
     *
     * CapacityPool przechowuje informację o całkowitej oraz dostępnej liczbie
     * miejsc dla danego eventu. Jest oddzielną encją, ponieważ dostępność będzie
     * później używana w etapach concurrency, locking i atomic SQL update.
     */
    private final CapacityPoolRepository capacityPoolRepository;

    /**
     * Constructor injection.
     *
     * To preferowany sposób wstrzykiwania zależności:
     * - zależności są jawne,
     * - pola mogą być final,
     * - łatwo napisać test jednostkowy,
     * - Spring nie musi używać refleksji do ustawiania pól.
     */
    public EventService(EventRepository eventRepository,
                        CapacityPoolRepository capacityPoolRepository) {
        this.eventRepository = eventRepository;
        this.capacityPoolRepository = capacityPoolRepository;
    }

    /**
     * Tworzy event oraz powiązaną z nim pulę dostępności.
     *
     * Metoda jest transakcyjna, bo zapisujemy dwa powiązane ze sobą rekordy:
     *
     * 1. Event
     * 2. CapacityPool
     *
     * Bez transakcji mogłoby dojść do sytuacji częściowego zapisu:
     * event zostałby zapisany, ale pula miejsc już nie. Dla systemu rezerwacji
     * byłby to stan niespójny.
     *
     * @Transactional zapewnia atomiczność operacji na poziomie aplikacji:
     * albo oba zapisy zostaną zatwierdzone, albo oba zostaną wycofane.
     */
    @Transactional
    public EventResponse create(EventCreateRequest request) {
        /*
         * Najpierw zapisujemy Event.
         *
         * Po save(...) encja dostaje ID wygenerowane przez bazę/Hibernate.
         * To ID jest potrzebne do powiązania CapacityPool z konkretnym eventem.
         */
        Event event = eventRepository.save(new Event(
                request.name(),
                request.city(),
                request.category(),
                request.startsAt()
        ));

        /*
         * Następnie tworzymy pulę miejsc dla eventu.
         *
         * totalCapacity określa liczbę wszystkich miejsc.
         * availableCapacity zwykle startuje z tą samą wartością i będzie
         * zmniejszane przy tworzeniu rezerwacji.
         */
        CapacityPool pool = capacityPoolRepository.save(
                new CapacityPool(event, request.totalCapacity())
        );

        /*
         * Na zewnątrz zwracamy DTO, a nie encje JPA.
         *
         * To ważne, bo encje są modelem persystencji, a nie kontraktem API.
         */
        return toResponse(event, pool);
    }

    /**
     * Pobiera event wraz z jego pulą dostępności.
     *
     * readOnly = true informuje Spring/Hibernate, że metoda nie powinna
     * modyfikować danych. To nie jest mechanizm bezpieczeństwa biznesowego,
     * ale może pomóc Hibernate w optymalizacji pracy persistence contextu.
     */
    @Transactional(readOnly = true)
    public EventResponse get(UUID eventId) {
        /*
         * Szukamy eventu po ID.
         *
         * Jeżeli event nie istnieje, rzucamy wyjątek aplikacyjny.
         * Globalny exception handler powinien zamienić go na HTTP 404.
         */
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));

        /*
         * Dla poprawnego response'u potrzebujemy też informacji o dostępności.
         *
         * Brak CapacityPool dla istniejącego eventu oznacza niespójność danych.
         * W tym projekcie mapujemy to również na NotFoundException, choć w bardziej
         * dojrzałym systemie można by rozważyć osobny błąd typu DataIntegrityException.
         */
        CapacityPool pool = capacityPoolRepository.findByEventId(eventId)
                .orElseThrow(() -> new NotFoundException("Capacity pool not found for event: " + eventId));

        return toResponse(event, pool);
    }

    /**
     * Mapuje encje JPA na DTO zwracane przez API.
     *
     * Ta metoda ukrywa przed kontrolerem szczegóły modelu persystencji.
     *
     * Dzięki temu API nie wystawia bezpośrednio encji JPA, co ogranicza ryzyko:
     * - przypadkowego lazy loadingu,
     * - cyklicznej serializacji relacji,
     * - ujawnienia pól technicznych,
     * - silnego związania kontraktu API ze strukturą tabel.
     */
    private EventResponse toResponse(Event event, CapacityPool pool) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getCity(),
                event.getCategory(),
                event.getStartsAt(),
                event.getStatus(),
                pool.getTotalCapacity(),
                pool.getAvailableCapacity()
        );
    }
}