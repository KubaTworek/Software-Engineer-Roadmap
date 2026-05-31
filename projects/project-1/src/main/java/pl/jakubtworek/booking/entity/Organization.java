package pl.jakubtworek.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Encja JPA reprezentująca organizację w systemie.
 *
 * Organization jest używana głównie w etapach:
 *
 * - SQL/performance,
 * - security,
 * - tenant boundary,
 * - autoryzacja managerów, HR i adminów organizacji.
 *
 * Organizacja grupuje:
 *
 * - eventy,
 * - użytkowników aplikacji,
 * - rezerwacje powiązane z eventami tej organizacji.
 *
 * Dzięki temu można sprawdzać reguły typu:
 *
 * - manager widzi tylko eventy swojej organizacji,
 * - HR widzi tylko użytkowników swojej organizacji,
 * - ORG_ADMIN zarządza tylko użytkownikami swojego tenantu.
 */
@Entity
@Table(name = "organizations")
public class Organization {

    /**
     * Główny identyfikator organizacji.
     *
     * UUID jest generowany aplikacyjnie w konstruktorze.
     */
    @Id
    private UUID id;

    /**
     * Nazwa organizacji.
     *
     * nullable = false oznacza, że nazwa jest wymagana.
     *
     * unique = true oznacza, że baza nie pozwoli utworzyć dwóch organizacji
     * z identyczną nazwą.
     *
     * Uwaga:
     * unikalność nazwy organizacji jest uproszczeniem. W realnym systemie dwie
     * organizacje mogą mieć podobne albo nawet identyczne nazwy prawne/handlowe.
     * Często lepszym identyfikatorem biznesowym jest np. slug, NIP, domain albo
     * osobny tenant identifier.
     */
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Moment utworzenia organizacji.
     *
     * updatable = false oznacza, że Hibernate nie powinien aktualizować tej kolumny
     * przy późniejszych zmianach encji.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Konstruktor bezargumentowy wymagany przez JPA.
     *
     * Jest protected, żeby kod aplikacyjny nie tworzył pustej, niepoprawnej
     * organizacji.
     */
    protected Organization() {
    }

    /**
     * Tworzy nową organizację.
     *
     * Konstruktor ustawia:
     * - UUID,
     * - nazwę,
     * - czas utworzenia.
     *
     * Walidacja pustej nazwy powinna być wykonana wcześniej, np. na DTO requestu.
     */
    public Organization(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.createdAt = Instant.now();
    }

    /**
     * Gettery udostępniają dane do mapowania DTO, zapytań i reguł security.
     *
     * Brak setterów jest celowy. Jeśli w przyszłości organizację będzie można
     * zmienić, lepiej dodać jawne metody domenowe, np.:
     *
     * rename(...)
     *
     * zamiast publicznego setName(...).
     */
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}