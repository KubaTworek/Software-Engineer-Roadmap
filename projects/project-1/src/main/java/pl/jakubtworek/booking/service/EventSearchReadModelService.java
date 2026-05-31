package pl.jakubtworek.booking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.nosql.EventSearchDocumentListResponse;
import pl.jakubtworek.booking.dto.nosql.EventSearchDocumentResponse;
import pl.jakubtworek.booking.entity.CapacityPool;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.entity.Organization;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.readmodel.EventSearchDocument;
import pl.jakubtworek.booking.readmodel.EventSearchReadModelStore;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;
import pl.jakubtworek.booking.repository.EventRepository;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za dokumentowy read model eventów.
 *
 * Ten serwis należy do etapu NoSQL/cache.
 *
 * Główna idea:
 *
 * - PostgreSQL jest źródłem prawdy.
 * - Event, CapacityPool i Reservation są przechowywane relacyjnie.
 * - Read model jest denormalizowanym dokumentem przygotowanym pod szybki odczyt.
 * - Dokument może być przechowywany np. w MongoDB albo implementacji in-memory.
 *
 * To jest przykład świadomego użycia NoSQL:
 * nie przenosimy całej domeny do MongoDB, tylko tworzymy dodatkowy model
 * zoptymalizowany pod konkretny access pattern.
 */
@Service
public class EventSearchReadModelService {

    /**
     * Repozytorium eventów z relacyjnej bazy danych.
     *
     * Używane podczas odbudowy dokumentu read modelu.
     */
    private final EventRepository eventRepository;

    /**
     * Repozytorium puli miejsc.
     *
     * Dostarcza totalCapacity i availableCapacity.
     *
     * Ważne: dostępność z read modelu jest tylko snapshotem.
     * Decyzja o faktycznej rezerwacji miejsca nadal musi przechodzić przez
     * PostgreSQL i atomowy update w CapacityPoolRepository.
     */
    private final CapacityPoolRepository capacityPoolRepository;

    /**
     * Repozytorium rezerwacji.
     *
     * Używane tutaj do agregacji liczby rezerwacji po statusach.
     *
     * Nie pobieramy wszystkich rezerwacji do Javy. Zamiast tego baza wykonuje:
     *
     * SELECT status, COUNT(*)
     * FROM reservations
     * WHERE event_id = ?
     * GROUP BY status
     */
    private final ReservationRepository reservationRepository;

    /**
     * Abstrakcja nad miejscem przechowywania read modelu.
     *
     * Implementacja może być:
     * - in-memory dla testów,
     * - MongoDB dla profilu realnego.
     *
     * Dzięki temu serwis nie musi znać szczegółów konkretnej technologii.
     */
    private final EventSearchReadModelStore store;

    /**
     * Constructor injection.
     *
     * Zależności są jawne, a serwis łatwiej testować.
     */
    public EventSearchReadModelService(EventRepository eventRepository,
                                       CapacityPoolRepository capacityPoolRepository,
                                       ReservationRepository reservationRepository,
                                       EventSearchReadModelStore store) {
        this.eventRepository = eventRepository;
        this.capacityPoolRepository = capacityPoolRepository;
        this.reservationRepository = reservationRepository;
        this.store = store;
    }

    /**
     * Odbudowuje dokument read modelu dla jednego eventu.
     *
     * Flow:
     *
     * 1. Pobierz event z PostgreSQL.
     * 2. Pobierz CapacityPool z PostgreSQL.
     * 3. Policz rezerwacje po statusach w PostgreSQL.
     * 4. Zbuduj denormalizowany EventSearchDocument.
     * 5. Zapisz dokument do read model store.
     *
     * readOnly = true dotyczy relacyjnej bazy danych.
     * Zapis do read modelu jest osobnym efektem ubocznym poza JPA.
     *
     * To pokazuje eventual consistency:
     * dokument jest aktualny na moment rebuilda, ale później może się zestarzeć.
     */
    @Transactional(readOnly = true)
    public EventSearchDocumentResponse rebuildOne(UUID eventId) {
        /*
         * PostgreSQL pozostaje źródłem prawdy dla danych eventu.
         */
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));

        /*
         * CapacityPool dostarcza snapshot dostępności.
         *
         * Snapshot zapisany do read modelu może się zdezaktualizować po kolejnej
         * rezerwacji albo anulowaniu.
         */
        CapacityPool pool = capacityPoolRepository.findByEventId(eventId)
                .orElseThrow(() -> new NotFoundException("Capacity pool not found for event: " + eventId));

        /*
         * Agregacja po stronie bazy.
         *
         * Nie ma sensu pobierać wszystkich rezerwacji i liczyć statusów w Javie,
         * jeśli SQL może zrobić GROUP BY.
         */
        Map<String, Long> reservationsByStatus = new LinkedHashMap<>();

        for (Object[] row : reservationRepository.countByStatusForEvent(eventId)) {
            reservationsByStatus.put(String.valueOf(row[0]), (Long) row[1]);
        }

        /*
         * Organizacja jest opcjonalna w obecnym modelu, bo starszy MVP pozwalał
         * tworzyć eventy bez organizacji.
         *
         * Po pełnym wdrożeniu tenant boundary lepiej wymagać organizacji dla eventu.
         */
        Organization organization = event.getOrganization();

        /*
         * Budujemy dokument pod odczyt.
         *
         * To jest denormalizacja: w jednym dokumencie trzymamy dane z kilku tabel:
         *
         * - events,
         * - organizations,
         * - capacity_pools,
         * - reservations aggregated by status.
         *
         * Dzięki temu endpoint search/read model nie musi przy każdym odczycie
         * robić kilku joinów i agregacji.
         */
        EventSearchDocument document = new EventSearchDocument(
                event.getId(),
                event.getName(),
                event.getCity(),
                event.getCategory(),
                event.getStartsAt(),
                organization == null ? null : organization.getId(),
                organization == null ? null : organization.getName(),
                pool.getTotalCapacity(),
                pool.getAvailableCapacity(),
                reservationsByStatus,
                Instant.now()
        );

        /*
         * Zapisujemy dokument do read model store.
         *
         * Dla implementacji MongoDB będzie to zapis dokumentu.
         * Dla implementacji in-memory będzie to zapis do mapy.
         */
        return toResponse(store.save(document));
    }

    /**
     * Pobiera dokument read modelu po eventId.
     *
     * Ten odczyt nie musi dotykać relacyjnych tabel.
     * Czyta gotowy dokument z EventSearchReadModelStore.
     *
     * Jeśli dokument nie istnieje, zwracamy błąd.
     * To może oznaczać, że read model nie został jeszcze odbudowany.
     */
    public EventSearchDocumentResponse get(UUID eventId) {
        return store.findByEventId(eventId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Event search document not found: " + eventId));
    }

    /**
     * Wyszukuje dokumenty po access patternie:
     *
     * city + category + limit
     *
     * To pokazuje modelowanie dokumentu pod zapytania.
     *
     * W relacyjnej bazie prawdopodobnie robilibyśmy query po tabeli events.
     * W MongoDB możemy mieć dokument EventSearchDocument przygotowany dokładnie
     * pod ekran listy/search.
     */
    public EventSearchDocumentListResponse search(String city, String category, int limit) {
        var items = store.search(city, category, limit).stream()
                .map(this::toResponse)
                .toList();

        return new EventSearchDocumentListResponse(items.size(), items);
    }

    /**
     * Czyści cały read model.
     *
     * To jest operacja edukacyjna/testowa.
     *
     * Pokazuje, że read model jest pochodny względem PostgreSQL i może zostać
     * odbudowany od zera.
     *
     * Produkcyjnie taka metoda powinna być:
     * - ograniczona administracyjnie,
     * - schowana za security,
     * - albo dostępna tylko jako job techniczny.
     */
    public void clear() {
        store.deleteAll();
    }

    /**
     * Mapuje wewnętrzny dokument read modelu na DTO odpowiedzi API.
     *
     * Nie zwracamy bezpośrednio EventSearchDocument, żeby nie wiązać kontraktu HTTP
     * z wewnętrzną strukturą storage.
     */
    private EventSearchDocumentResponse toResponse(EventSearchDocument document) {
        return new EventSearchDocumentResponse(
                document.getEventId(),
                document.getName(),
                document.getCity(),
                document.getCategory(),
                document.getStartsAt(),
                document.getOrganizationId(),
                document.getOrganizationName(),
                document.getTotalCapacity(),
                document.getAvailableCapacity(),
                document.getReservationsByStatus(),
                document.getRebuiltAt()
        );
    }
}