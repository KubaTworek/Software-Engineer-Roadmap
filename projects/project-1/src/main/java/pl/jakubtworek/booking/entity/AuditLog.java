package pl.jakubtworek.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Encja JPA reprezentująca wpis audytowy.
 *
 * AuditLog służy do zapisywania istotnych zdarzeń w systemie, np.:
 *
 * - potwierdzenia rezerwacji,
 * - timeoutu płatności,
 * - błędu przy zewnętrznej notyfikacji,
 * - wykonania operacji administracyjnej.
 *
 * W tym projekcie audyt pojawia się głównie w etapie asynchroniczności,
 * gdzie po potwierdzeniu rezerwacji wykonywany jest dodatkowy side-effect:
 * zapis wpisu audytowego.
 *
 * To jest prosty audyt techniczno-biznesowy, nie pełny event sourcing.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    /**
     * Główny identyfikator wpisu audytowego.
     *
     * UUID jest generowany aplikacyjnie w konstruktorze.
     */
    @Id
    private UUID id;

    /**
     * Identyfikator rezerwacji, której dotyczy wpis.
     *
     * Celowo przechowujemy tutaj samo UUID, a nie relację @ManyToOne do Reservation.
     *
     * Zalety takiego uproszczenia:
     * - audyt jest luźniej powiązany z modelem domenowym,
     * - zapis audytu nie wymaga dociągania encji Reservation,
     * - łatwiej zapisać wpis nawet wtedy, gdy nie chcemy tworzyć relacji JPA.
     *
     * Wady:
     * - baza nie wymusza tak silnie spójności relacyjnej, jeśli nie ma FK,
     * - nie można wygodnie nawigować auditLog.getReservation().
     */
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    /**
     * Typ zdarzenia audytowego.
     *
     * Przykłady:
     * - RESERVATION_CONFIRMED,
     * - PAYMENT_TIMEOUT,
     * - EMAIL_SENT,
     * - EXTERNAL_NOTIFICATION_FAILED.
     *
     * Limit length = 80 chroni przed przypadkowo zbyt długą nazwą typu.
     */
    @Column(nullable = false, length = 80)
    private String type;

    /**
     * Opis zdarzenia audytowego.
     *
     * To pole powinno zawierać czytelny, krótki komunikat diagnostyczny.
     *
     * Limit 1000 znaków zapobiega przypadkowemu zapisaniu ogromnych payloadów,
     * stack trace’ów albo odpowiedzi z systemów zewnętrznych.
     */
    @Column(nullable = false, length = 1000)
    private String message;

    /**
     * Moment utworzenia wpisu audytowego.
     *
     * updatable = false oznacza, że wpis audytowy jest append-only:
     * po utworzeniu nie powinien zmieniać czasu powstania.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Konstruktor bezargumentowy wymagany przez JPA.
     *
     * Jest protected, żeby kod aplikacyjny nie tworzył pustych, niepoprawnych
     * wpisów audytowych.
     */
    protected AuditLog() {
    }

    /**
     * Tworzy nowy wpis audytowy.
     *
     * Konstruktor ustawia:
     * - UUID,
     * - reservationId,
     * - typ zdarzenia,
     * - wiadomość,
     * - czas utworzenia.
     */
    public AuditLog(UUID reservationId, String type, String message) {
        this.id = UUID.randomUUID();
        this.reservationId = reservationId;
        this.type = type;
        this.message = message;
        this.createdAt = Instant.now();
    }

    /**
     * Gettery udostępniają dane do odczytu, mapowania DTO i testów.
     *
     * Brak setterów jest celowy:
     * wpis audytowy po utworzeniu powinien być traktowany jako niemutowalny rekord.
     */
    public UUID getId() {
        return id;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}