package pl.jakubtworek.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Encja JPA reprezentująca próbę wysłania komunikatu wychodzącego.
 *
 * OutboundMessage jest używany w etapie asynchroniczności.
 *
 * Przykładowe komunikaty wychodzące:
 *
 * - email z potwierdzeniem rezerwacji,
 * - powiadomienie zewnętrznego systemu,
 * - webhook,
 * - komunikat integracyjny.
 *
 * Ta encja nie jest pełną implementacją Outbox Pattern.
 * Jest prostym zapisem technicznym pokazującym:
 *
 * - jaki kanał był użyty,
 * - czy wysyłka się udała,
 * - jaki payload próbowano wysłać,
 * - jaki błąd wystąpił.
 *
 * Dzięki temu side-effecty asynchroniczne są widoczne w bazie i można je testować.
 */
@Entity
@Table(name = "outbound_messages")
public class OutboundMessage {

    /**
     * Główny identyfikator komunikatu wychodzącego.
     *
     * UUID jest generowany aplikacyjnie przy tworzeniu komunikatu.
     */
    @Id
    private UUID id;

    /**
     * Identyfikator rezerwacji, której dotyczy komunikat.
     *
     * Celowo przechowujemy samo UUID, a nie relację @ManyToOne do Reservation.
     *
     * Zalety:
     * - prostszy zapis side-effectu,
     * - brak potrzeby dociągania encji Reservation,
     * - mniejsze ryzyko lazy loadingu,
     * - luźniejsze powiązanie technicznego logu wysyłki z domeną.
     */
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    /**
     * Kanał wysyłki.
     *
     * Przykłady:
     * - EMAIL,
     * - EXTERNAL_NOTIFICATION,
     * - WEBHOOK.
     *
     * Limit length = 80 chroni przed przypadkowo zbyt długą nazwą kanału.
     */
    @Column(nullable = false, length = 80)
    private String channel;

    /**
     * Status wysyłki.
     *
     * Aktualnie używane wartości to:
     * - SENT,
     * - FAILED.
     *
     * W bardziej rozbudowanej wersji można byłoby użyć enuma, np.
     * OutboundMessageStatus, zamiast zwykłego Stringa.
     */
    @Column(nullable = false, length = 40)
    private String status;

    /**
     * Payload komunikatu.
     *
     * To uproszczony zapis treści albo danych wysyłanych do zewnętrznego kanału.
     *
     * Limit 1000 znaków jest zabezpieczeniem przed przypadkowym zapisaniem
     * ogromnego JSON-a albo danych wrażliwych.
     *
     * Produkcyjnie trzeba uważać, żeby nie zapisywać tu sekretów, tokenów,
     * pełnych danych płatności ani danych osobowych bez potrzeby.
     */
    @Column(nullable = false, length = 1000)
    private String payload;

    /**
     * Komunikat błędu, jeśli wysyłka się nie udała.
     *
     * Dla statusu SENT zwykle będzie null.
     *
     * Dla statusu FAILED zawiera skróconą informację o wyjątku.
     */
    @Column(length = 1000)
    private String errorMessage;

    /**
     * Moment utworzenia wpisu.
     *
     * updatable = false oznacza, że czas utworzenia nie powinien być zmieniany
     * przy późniejszych aktualizacjach.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Konstruktor bezargumentowy wymagany przez JPA.
     *
     * Jest protected, żeby kod aplikacyjny nie tworzył pustych,
     * niepoprawnych komunikatów.
     */
    protected OutboundMessage() {
    }

    /**
     * Prywatny konstruktor używany przez metody fabryczne sent(...) i failed(...).
     *
     * Dzięki temu kontrolujemy dozwolone sposoby tworzenia OutboundMessage.
     * Kod zewnętrzny nie powinien ręcznie przekazywać dowolnego statusu.
     */
    private OutboundMessage(UUID reservationId,
                            String channel,
                            String status,
                            String payload,
                            String errorMessage) {
        this.id = UUID.randomUUID();
        this.reservationId = reservationId;
        this.channel = channel;
        this.status = status;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.createdAt = Instant.now();
    }

    /**
     * Tworzy wpis oznaczający poprawnie wysłany komunikat.
     *
     * Status zostaje ustawiony na SENT.
     */
    public static OutboundMessage sent(UUID reservationId, String channel, String payload) {
        return new OutboundMessage(reservationId, channel, "SENT", payload, null);
    }

    /**
     * Tworzy wpis oznaczający nieudaną wysyłkę.
     *
     * Status zostaje ustawiony na FAILED.
     *
     * Z wyjątku zapisujemy tylko klasę błędu i message, a nie cały stack trace.
     * To ogranicza rozmiar wpisu i ryzyko zapisania zbyt dużej ilości danych.
     */
    public static OutboundMessage failed(UUID reservationId,
                                         String channel,
                                         String payload,
                                         Throwable error) {
        String message = error == null
                ? "unknown"
                : error.getClass().getSimpleName() + ": " + error.getMessage();

        return new OutboundMessage(reservationId, channel, "FAILED", payload, message);
    }

    /**
     * Gettery udostępniają stan encji do testów, debugowania i ewentualnych widoków.
     *
     * Brak setterów jest celowy.
     * Komunikat wychodzący jest traktowany jako rekord zdarzenia technicznego.
     */
    public UUID getId() {
        return id;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public String getChannel() {
        return channel;
    }

    public String getStatus() {
        return status;
    }

    public String getPayload() {
        return payload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}