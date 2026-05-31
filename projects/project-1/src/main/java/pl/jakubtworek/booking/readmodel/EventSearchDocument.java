package pl.jakubtworek.booking.readmodel;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Dokument read modelu eventu.
 *
 * Ta klasa jest mapowana na kolekcję MongoDB:
 *
 * event_search_documents
 *
 * To nie jest encja JPA i nie jest źródłem prawdy.
 *
 * Źródłem prawdy nadal są tabele relacyjne w PostgreSQL:
 *
 * - events,
 * - organizations,
 * - capacity_pools,
 * - reservations.
 *
 * EventSearchDocument jest denormalizowanym widokiem przygotowanym pod szybki odczyt,
 * np. ekran wyszukiwania eventów.
 *
 * Główna idea:
 *
 * - zapis i reguły biznesowe zostają w modelu relacyjnym,
 * - MongoDB/read model służy do wygodnego i szybkiego odczytu,
 * - dokument może być chwilowo nieaktualny,
 * - dokument można odbudować z PostgreSQL.
 */
@Document(collection = "event_search_documents")
@CompoundIndex(
        name = "idx_city_category_starts_at",
        def = "{'city': 1, 'category': 1, 'startsAt': 1}"
)
public class EventSearchDocument {

    /**
     * Identyfikator dokumentu w MongoDB.
     *
     * Używamy tego samego UUID, które ma Event w PostgreSQL.
     *
     * Dzięki temu:
     * - łatwo znaleźć dokument po eventId,
     * - rebuild dokumentu działa jak upsert,
     * - nie trzeba utrzymywać osobnego identyfikatora read modelu.
     */
    @Id
    private UUID eventId;

    /**
     * Nazwa eventu skopiowana z relacyjnej encji Event.
     *
     * To jest denormalizacja: trzymamy dane eventu bezpośrednio w dokumencie,
     * żeby nie robić joinów przy odczycie.
     */
    private String name;

    /**
     * Miasto eventu.
     *
     * To pole jest częścią access patternu dla wyszukiwania dokumentów:
     *
     * city + category + startsAt
     *
     * W MongoDB warto mieć indeks obejmujący city, category i startsAt.
     */
    private String city;

    /**
     * Kategoria eventu.
     *
     * Razem z city służy do filtrowania eventów w read modelu.
     */
    private String category;

    /**
     * Data rozpoczęcia eventu.
     *
     * Używana do sortowania wyników wyszukiwania, np. najbliższe eventy pierwsze.
     */
    private OffsetDateTime startsAt;

    /**
     * Identyfikator organizacji.
     *
     * To pole pochodzi z relacyjnej encji Organization.
     *
     * Jest skopiowane do dokumentu po to, żeby lista/search nie musiała robić
     * dodatkowego odczytu organizacji.
     */
    private UUID organizationId;

    /**
     * Nazwa organizacji.
     *
     * To również jest denormalizacja.
     *
     * Jeśli nazwa organizacji zmieni się w PostgreSQL, dokument w MongoDB będzie
     * miał starą nazwę aż do kolejnego rebuilda read modelu.
     */
    private String organizationName;

    /**
     * Całkowita liczba miejsc dla eventu.
     *
     * Pochodzi z CapacityPool w PostgreSQL.
     */
    private int totalCapacity;

    /**
     * Snapshot aktualnie dostępnych miejsc.
     *
     * Ważne:
     * ta wartość może być nieaktualna.
     *
     * Nie wolno podejmować krytycznej decyzji o sprzedaży miejsca na podstawie
     * tego pola. Rezerwacja musi przejść przez PostgreSQL i atomowy update
     * CapacityPool.
     */
    private int availableCapacity;

    /**
     * Liczba rezerwacji pogrupowana po statusie.
     *
     * Przykład:
     *
     * {
     *   "PENDING": 5,
     *   "CONFIRMED": 20,
     *   "CANCELLED": 2
     * }
     *
     * To pole powstaje przez agregację w PostgreSQL i jest zapisywane jako część
     * dokumentu read modelu.
     */
    private Map<String, Long> reservationsByStatus;

    /**
     * Moment ostatniej odbudowy dokumentu.
     *
     * To pole pomaga świadomie ocenić świeżość read modelu.
     *
     * Jeśli rebuiltAt jest stare, dokument może nie odzwierciedlać aktualnego
     * stanu PostgreSQL.
     */
    private Instant rebuiltAt;

    /**
     * Konstruktor bezargumentowy wymagany przez Spring Data MongoDB.
     *
     * Jest protected, bo kod aplikacyjny powinien tworzyć dokument przez pełny
     * konstruktor z wymaganymi polami.
     */
    protected EventSearchDocument() {
    }

    /**
     * Tworzy kompletny dokument read modelu eventu.
     *
     * Konstruktor przyjmuje dane z kilku źródeł relacyjnych:
     *
     * - Event,
     * - Organization,
     * - CapacityPool,
     * - agregacje Reservation.
     *
     * To właśnie jest dokumentowy read model: jeden dokument przygotowany pod
     * konkretny odczyt, zamiast normalizacji jak w relacyjnej bazie.
     */
    public EventSearchDocument(UUID eventId,
                               String name,
                               String city,
                               String category,
                               OffsetDateTime startsAt,
                               UUID organizationId,
                               String organizationName,
                               int totalCapacity,
                               int availableCapacity,
                               Map<String, Long> reservationsByStatus,
                               Instant rebuiltAt) {
        this.eventId = eventId;
        this.name = name;
        this.city = city;
        this.category = category;
        this.startsAt = startsAt;
        this.organizationId = organizationId;
        this.organizationName = organizationName;
        this.totalCapacity = totalCapacity;
        this.availableCapacity = availableCapacity;
        this.reservationsByStatus = reservationsByStatus;
        this.rebuiltAt = rebuiltAt;
    }

    /**
     * Gettery są używane przez:
     *
     * - Spring Data MongoDB,
     * - warstwę serwisową,
     * - mapowanie na DTO odpowiedzi API.
     *
     * Brak setterów jest celowy: dokument powinien być traktowany jako snapshot.
     * Jeśli dane się zmienią, prościej i bezpieczniej odbudować dokument niż
     * mutować losowe pola w wielu miejscach.
     */
    public UUID getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public String getCategory() {
        return category;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public int getAvailableCapacity() {
        return availableCapacity;
    }

    public Map<String, Long> getReservationsByStatus() {
        return reservationsByStatus;
    }

    public Instant getRebuiltAt() {
        return rebuiltAt;
    }
}