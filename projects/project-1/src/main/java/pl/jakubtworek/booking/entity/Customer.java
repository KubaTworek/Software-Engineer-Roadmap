package pl.jakubtworek.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Encja JPA reprezentująca klienta dokonującego rezerwacji.
 *
 * Customer oznacza osobę, która rezerwuje miejsce na event.
 *
 * W tym projekcie warto odróżnić Customer od AppUser:
 *
 * - Customer to klient końcowy widoczny w kontekście rezerwacji,
 * - AppUser to konto systemowe używane do logowania, ról i autoryzacji.
 *
 * W prostym MVP klient jest identyfikowany głównie po emailu.
 */
@Entity
@Table(name = "customers")
public class Customer {

    /**
     * Główny identyfikator klienta.
     *
     * UUID jest generowany aplikacyjnie w konstruktorze.
     */
    @Id
    private UUID id;

    /**
     * Imię i nazwisko klienta.
     *
     * nullable = false oznacza, że baza nie powinna dopuścić pustej wartości NULL.
     *
     * To nie zastępuje walidacji requestu. Walidacja wejścia powinna być wykonana
     * na DTO, np. przez @NotBlank w ReservationCreateRequest.
     */
    @Column(nullable = false)
    private String fullName;

    /**
     * Email klienta.
     *
     * unique = true oznacza, że w bazie może istnieć tylko jeden Customer
     * z danym adresem email.
     *
     * ReservationService używa emaila do znalezienia istniejącego klienta albo
     * utworzenia nowego.
     *
     * Uwaga:
     * przy równoległych requestach dla tego samego nowego emaila może dojść
     * do konfliktu unikalności. To warto obsłużyć testem lub retry.
     */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * Moment utworzenia klienta.
     *
     * updatable = false oznacza, że Hibernate nie powinien aktualizować tej kolumny
     * przy późniejszych zmianach encji.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Konstruktor bezargumentowy wymagany przez JPA.
     *
     * Jest protected, żeby kod aplikacyjny nie tworzył pustego, niepoprawnego klienta.
     */
    protected Customer() {
    }

    /**
     * Tworzy nowego klienta.
     *
     * Konstruktor ustawia:
     * - UUID,
     * - fullName,
     * - email,
     * - createdAt.
     *
     * Warto pamiętać, że konstruktor nie waliduje formatu emaila.
     * Taka walidacja powinna być w DTO wejściowym albo osobnej regule domenowej.
     */
    public Customer(String fullName, String email) {
        this.id = UUID.randomUUID();
        this.fullName = fullName;
        this.email = email;
        this.createdAt = Instant.now();
    }

    /**
     * Gettery udostępniają dane do mapowania DTO, zapytań i testów.
     *
     * Brak setterów jest celowy. Jeśli w przyszłości klient będzie mógł zmienić
     * email lub nazwisko, lepiej dodać jawne metody domenowe, np.:
     *
     * changeEmail(...)
     * rename(...)
     *
     * zamiast publicznych setterów dla wszystkich pól.
     */
    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}