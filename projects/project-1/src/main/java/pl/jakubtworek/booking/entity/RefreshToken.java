package pl.jakubtworek.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Encja JPA reprezentująca refresh token użytkownika.
 *
 * Refresh token służy do wydawania nowych access tokenów bez ponownego logowania.
 *
 * Ważne:
 * w bazie nie przechowujemy surowej wartości refresh tokenu.
 * Przechowujemy tylko jego hash.
 *
 * Dzięki temu wyciek bazy nie oznacza automatycznie wycieku aktywnych refresh tokenów.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        /**
         * Indeks po token_hash.
         *
         * AuthService.refresh(...) szuka refresh tokenu właśnie po hashu.
         *
         * unique = true wymusza, że ten sam hash tokenu nie pojawi się dwa razy.
         * Przy losowych tokenach kolizja jest skrajnie mało prawdopodobna,
         * ale unikalność jest dobrym dodatkowym zabezpieczeniem.
         */
        @Index(name = "idx_refresh_tokens_token_hash", columnList = "token_hash", unique = true),

        /**
         * Indeks po user_id.
         *
         * Przydaje się, jeśli chcesz później:
         * - znaleźć wszystkie refresh tokeny użytkownika,
         * - unieważnić wszystkie sesje użytkownika,
         * - wykrywać reuse tokenów,
         * - usuwać stare tokeny danego użytkownika.
         */
        @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id")
})
public class RefreshToken {

    /**
     * Główny identyfikator rekordu refresh tokenu.
     *
     * UUID jest generowany aplikacyjnie w konstruktorze.
     */
    @Id
    private UUID id;

    /**
     * Użytkownik, do którego należy refresh token.
     *
     * Relacja ManyToOne oznacza:
     * - jeden refresh token należy do jednego użytkownika,
     * - jeden użytkownik może mieć wiele refresh tokenów,
     *   np. z różnych urządzeń albo sesji.
     *
     * optional = false i nullable = false oznaczają, że refresh token nie może
     * istnieć bez użytkownika.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /**
     * Hash refresh tokenu.
     *
     * Surowy refresh token jest zwracany klientowi tylko raz.
     * W bazie zapisujemy wynik SHA-256.
     *
     * Dla refresh tokenu SHA-256 jest akceptowalne, bo token jest długi,
     * losowy i ma wysoką entropię.
     *
     * Dla haseł użytkowników nie używamy samego SHA-256.
     * Hasła powinny być hashowane algorytmem typu BCrypt, Argon2 albo PBKDF2.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    /**
     * Moment utworzenia refresh tokenu.
     *
     * updatable = false oznacza, że czas utworzenia nie powinien być zmieniany.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Moment wygaśnięcia refresh tokenu.
     *
     * Po tej dacie token nie powinien już pozwalać na wydanie nowego access tokenu.
     */
    @Column(nullable = false)
    private Instant expiresAt;

    /**
     * Moment unieważnienia refresh tokenu.
     *
     * Jeśli revokedAt jest null, token nie został ręcznie unieważniony.
     * Jeśli revokedAt ma wartość, token był już użyty albo cofnięty.
     *
     * W projekcie Stage 7 refresh token jest rotowany:
     * po użyciu stary token dostaje revokedAt i nie może być użyty ponownie.
     */
    @Column
    private Instant revokedAt;

    /**
     * Konstruktor bezargumentowy wymagany przez JPA.
     *
     * Jest protected, żeby kod aplikacyjny nie tworzył pustego, niepoprawnego
     * refresh tokenu.
     */
    protected RefreshToken() {
    }

    /**
     * Tworzy nowy refresh token dla użytkownika.
     *
     * Konstruktor przyjmuje już hash tokenu, nie surową wartość.
     *
     * Ustawia:
     * - UUID,
     * - użytkownika,
     * - tokenHash,
     * - createdAt,
     * - expiresAt.
     */
    public RefreshToken(AppUser user, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    /**
     * Unieważnia refresh token.
     *
     * Ustawienie revokedAt oznacza, że token nie powinien być już aktywny.
     *
     * W rotacji refresh tokenów ta metoda jest wywoływana przed wydaniem nowego
     * refresh tokenu.
     */
    public void revoke() {
        this.revokedAt = Instant.now();
    }

    /**
     * Sprawdza, czy refresh token jest aktywny.
     *
     * Token jest aktywny tylko wtedy, gdy:
     *
     * - nie został unieważniony,
     * - jego expiresAt jest w przyszłości.
     */
    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    /**
     * Gettery udostępniają dane do AuthService, testów i repozytoriów.
     *
     * Brak setterów jest celowy.
     * Token powinien zmieniać stan tylko przez jawne metody, np. revoke().
     */
    public UUID getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}