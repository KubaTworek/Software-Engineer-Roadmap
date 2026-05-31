package pl.jakubtworek.booking.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Encja JPA reprezentująca rezerwację miejsca na event.
 *
 * Reservation jest centralną encją procesu rezerwacji:
 *
 * - łączy event z klientem,
 * - przechowuje status rezerwacji,
 * - pamięta daty utworzenia, potwierdzenia i anulowania,
 * - przechowuje organizationId pośrednio przez relację do Organization.
 *
 * Sama rezerwacja nie zmniejsza dostępności miejsc.
 * Zmniejszenie dostępności dzieje się w CapacityPool, zwykle przez atomowy SQL update.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    /**
     * Główny identyfikator rezerwacji.
     *
     * UUID jest generowany aplikacyjnie w konstruktorze.
     */
    @Id
    private UUID id;

    /**
     * Event, którego dotyczy rezerwacja.
     *
     * Relacja ManyToOne oznacza:
     * - jedna rezerwacja dotyczy jednego eventu,
     * - jeden event może mieć wiele rezerwacji.
     *
     * optional = false i nullable = false oznaczają, że rezerwacja nie może istnieć
     * bez eventu.
     *
     * FetchType.LAZY ogranicza automatyczne pobieranie eventu.
     * Jeśli event jest potrzebny do DTO, repozytorium powinno użyć fetch join
     * albo EntityGraph.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    /**
     * Organizacja, do której należy rezerwacja.
     *
     * To pole jest denormalizacją relacji:
     *
     * Reservation -> Event -> Organization
     *
     * Przechowywanie organization bezpośrednio w Reservation ułatwia zapytania typu:
     *
     * "pokaż rezerwacje organizacji X po statusie Y"
     *
     * Dzięki temu zapytanie nie musi zawsze przechodzić przez event.
     *
     * Minusem jest konieczność utrzymania spójności:
     * organization w Reservation powinno odpowiadać organization z Event.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    /**
     * Klient, który wykonał rezerwację.
     *
     * Relacja ManyToOne:
     * - jedna rezerwacja należy do jednego klienta,
     * - jeden klient może mieć wiele rezerwacji.
     *
     * optional = false i nullable = false oznaczają, że rezerwacja wymaga klienta.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /**
     * Aktualny status rezerwacji.
     *
     * EnumType.STRING zapisuje status jako tekst, np.:
     *
     * - PENDING,
     * - CONFIRMED,
     * - CANCELLED,
     * - PAYMENT_TIMEOUT.
     *
     * To bezpieczniejsze niż EnumType.ORDINAL, bo zmiana kolejności enumów
     * nie uszkodzi znaczenia danych w bazie.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    /**
     * Moment utworzenia rezerwacji.
     *
     * updatable = false oznacza, że Hibernate nie powinien aktualizować tej kolumny
     * przy późniejszych zmianach encji.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Moment potwierdzenia rezerwacji.
     *
     * Dla rezerwacji w statusie PENDING, CANCELLED albo PAYMENT_TIMEOUT może być null.
     */
    @Column
    private Instant confirmedAt;

    /**
     * Moment anulowania rezerwacji.
     *
     * Dla rezerwacji, które nie zostały anulowane, będzie null.
     */
    @Column
    private Instant cancelledAt;

    /**
     * Konstruktor bezargumentowy wymagany przez JPA.
     *
     * Jest protected, żeby kod aplikacyjny nie tworzył pustych, niepoprawnych
     * rezerwacji.
     */
    protected Reservation() {
    }

    /**
     * Tworzy nową rezerwację w statusie PENDING.
     *
     * Rezerwacja dostaje:
     * - nowe UUID,
     * - event,
     * - organizację skopiowaną z eventu,
     * - klienta,
     * - status PENDING,
     * - createdAt ustawione na aktualny czas.
     *
     * Uwaga:
     * this.organization = event.getOrganization() może dotknąć lazy relacji Event -> Organization.
     * W tym konstruktorze zwykle event jest już zarządzany przez persistence context,
     * więc jest to akceptowalne, ale warto być świadomym zależności.
     */
    public Reservation(Event event, Customer customer) {
        this.id = UUID.randomUUID();
        this.event = event;
        this.organization = event.getOrganization();
        this.customer = customer;
        this.status = ReservationStatus.PENDING;
        this.createdAt = Instant.now();
    }

    /**
     * Potwierdza rezerwację.
     *
     * Reguła biznesowa:
     * tylko rezerwacja w statusie PENDING może zostać potwierdzona.
     *
     * Jeśli status jest inny, metoda rzuca wyjątek.
     * Dzięki temu nie da się przypadkowo potwierdzić rezerwacji anulowanej,
     * już potwierdzonej albo po timeoutcie płatności.
     */
    public void confirm() {
        if (status != ReservationStatus.PENDING) {
            throw new IllegalStateException("Only pending reservation can be confirmed");
        }

        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
    }

    /**
     * Oznacza rezerwację jako PAYMENT_TIMEOUT.
     *
     * Ta metoda jest używana w asynchronicznym flow płatności.
     *
     * Reguła:
     * tylko rezerwacja PENDING może przejść do PAYMENT_TIMEOUT.
     *
     * Nie ustawiamy cancelledAt, bo timeout płatności nie jest tym samym
     * co anulowanie przez użytkownika lub system.
     */
    public void markPaymentTimeout() {
        if (status != ReservationStatus.PENDING) {
            throw new IllegalStateException("Only pending reservation can be marked as payment timeout");
        }

        this.status = ReservationStatus.PAYMENT_TIMEOUT;
    }

    /**
     * Anuluje rezerwację.
     *
     * Zwraca boolean, bo serwis musi wiedzieć, czy anulowanie naprawdę nastąpiło.
     *
     * true oznacza:
     * - status został zmieniony na CANCELLED,
     * - można zwolnić miejsce w CapacityPool.
     *
     * false oznacza:
     * - rezerwacja była już anulowana,
     * - nie należy drugi raz zwalniać miejsca.
     *
     * W podstawowej wersji projektu rezerwacji CONFIRMED nie można anulować.
     * To celowe uproszczenie MVP. W realnym systemie mogłaby istnieć osobna polityka:
     *
     * - anulowanie do 24h przed eventem,
     * - zwrot płatności,
     * - opłata manipulacyjna,
     * - status REFUNDED.
     */
    public boolean cancel() {
        if (status == ReservationStatus.CANCELLED) {
            return false;
        }

        if (status == ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("Confirmed reservation cannot be cancelled in the base version");
        }

        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = Instant.now();

        return true;
    }

    /**
     * Gettery udostępniają stan encji do mapowania DTO, zapytań i testów.
     *
     * Brak setterów jest świadomy:
     * status rezerwacji powinien zmieniać się przez metody domenowe:
     *
     * - confirm(),
     * - markPaymentTimeout(),
     * - cancel().
     *
     * Dzięki temu reguły przejść statusów są skupione w jednym miejscu,
     * a nie rozproszone po serwisach.
     */
    public UUID getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Customer getCustomer() {
        return customer;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}