package pl.jakubtworek.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Encja JPA reprezentująca event, na który można tworzyć rezerwacje.
 *
 * Encja jest mapowana na tabelę "events".
 *
 * W tym projekcie Event przechowuje podstawowe informacje opisowe:
 * - nazwę,
 * - miasto,
 * - kategorię,
 * - datę rozpoczęcia,
 * - status publikacji,
 * - organizację, do której event należy.
 *
 * Sama dostępność miejsc nie znajduje się w tej encji.
 * Jest wydzielona do CapacityPool, ponieważ dostępność jest krytyczna
 * dla concurrency i wymaga osobnych strategii aktualizacji.
 */
@Entity
@Table(name = "events")
public class Event {

    /**
     * Główny identyfikator eventu.
     *
     * UUID jest generowany w konstruktorze aplikacyjnie przez UUID.randomUUID().
     *
     * Alternatywnie można byłoby użyć:
     *
     * @GeneratedValue
     *
     * i pozwolić bazie/Hibernate generować ID. Tutaj ręczne ustawianie UUID
     * jest proste i wystarczające.
     */
    @Id
    private UUID id;

    /**
     * Organizacja, do której należy event.
     *
     * Relacja ManyToOne oznacza:
     * - jeden event należy do jednej organizacji,
     * - jedna organizacja może mieć wiele eventów.
     *
     * FetchType.LAZY jest dobrym defaultem dla relacji ManyToOne w aplikacjach,
     * w których nie zawsze potrzebujemy danych organizacji przy pobieraniu eventu.
     *
     * Uwaga:
     * Jeśli spróbujesz odczytać organization poza aktywną transakcją, możesz
     * dostać LazyInitializationException. Dlatego endpointy powinny używać DTO,
     * fetch join, EntityGraph albo projection, jeśli potrzebują danych organizacji.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    /**
     * Nazwa eventu.
     *
     * nullable = false oznacza ograniczenie po stronie mapowania JPA i schematu bazy.
     * To nie zastępuje walidacji requestu, ale pomaga utrzymać spójność danych.
     */
    @Column(nullable = false)
    private String name;

    /**
     * Miasto eventu.
     *
     * To pole jest używane w zapytaniach wyszukujących eventy, np.:
     *
     * city + category + startsAt
     *
     * Dlatego w etapie SQL/performance pojawia się indeks złożony obejmujący city.
     */
    @Column(nullable = false)
    private String city;

    /**
     * Kategoria eventu, np. music, sport, conference.
     *
     * Razem z city i startsAt tworzy typowy access pattern dla wyszukiwania:
     *
     * "pokaż eventy w danym mieście, danej kategorii, od konkretnej daty".
     */
    @Column(nullable = false)
    private String category;

    /**
     * Data i czas rozpoczęcia eventu.
     *
     * OffsetDateTime przechowuje czas razem z offsetem strefy czasowej.
     *
     * Przy pracy z czasem warto być konsekwentnym:
     * - w API używać formatu ISO-8601,
     * - w bazie wiedzieć, czy kolumna przechowuje timestamp with time zone,
     * - w testach nie mieszać lokalnego czasu z UTC bez kontroli.
     */
    @Column(nullable = false)
    private OffsetDateTime startsAt;

    /**
     * Status eventu.
     *
     * EnumType.STRING zapisuje nazwę enuma jako tekst, np. "PUBLISHED".
     *
     * To jest bezpieczniejsze niż EnumType.ORDINAL, bo zmiana kolejności wartości
     * w enumie nie psuje danych zapisanych w bazie.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    /**
     * Moment utworzenia eventu.
     *
     * updatable = false oznacza, że Hibernate nie powinien aktualizować tej kolumny
     * przy późniejszych zmianach encji.
     *
     * W bardziej rozbudowanym projekcie można rozważyć @CreationTimestamp,
     * ale ręczne ustawienie Instant.now() jest czytelne dla MVP.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Konstruktor bezargumentowy wymagany przez JPA.
     *
     * Jest protected, bo kod aplikacyjny nie powinien tworzyć pustego Eventu.
     * Hibernate nadal może użyć tego konstruktora przez refleksję.
     */
    protected Event() {
    }

    /**
     * Konstruktor pomocniczy dla eventu bez przypisanej organizacji.
     *
     * Taki wariant był wygodny w początkowym MVP, zanim doszły tenanty,
     * organizacje i security.
     *
     * Deleguje do pełnego konstruktora, przekazując organization = null.
     */
    public Event(String name, String city, String category, OffsetDateTime startsAt) {
        this(null, name, city, category, startsAt);
    }

    /**
     * Główny konstruktor domenowy.
     *
     * Ustawia wszystkie wymagane pola oraz wartości domyślne:
     *
     * - id jako UUID.randomUUID(),
     * - status jako PUBLISHED,
     * - createdAt jako Instant.now().
     *
     * Dzięki temu nowy Event od razu jest w sensownym stanie.
     */
    public Event(Organization organization, String name, String city, String category, OffsetDateTime startsAt) {
        this.id = UUID.randomUUID();
        this.organization = organization;
        this.name = name;
        this.city = city;
        this.category = category;
        this.startsAt = startsAt;
        this.status = EventStatus.PUBLISHED;
        this.createdAt = Instant.now();
    }

    /**
     * Gettery są potrzebne dla mapowania DTO i logiki aplikacyjnej.
     *
     * Brak setterów jest świadomą decyzją:
     * encja nie jest dowolnie modyfikowalnym workiem na dane.
     *
     * Jeśli w przyszłości event będzie można anulować, opublikować lub zmienić,
     * lepiej dodać metody domenowe typu:
     *
     * cancel()
     * publish()
     * reschedule(...)
     *
     * zamiast publicznych setterów dla wszystkich pól.
     */
    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
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

    public EventStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}