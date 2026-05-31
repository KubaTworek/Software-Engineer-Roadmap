package pl.jakubtworek.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Encja JPA reprezentująca użytkownika aplikacji.
 *
 * AppUser jest używany w etapie Security i autoryzacja.
 *
 * Ta encja przechowuje informacje potrzebne do:
 *
 * - logowania użytkownika,
 * - przypisania użytkownika do organizacji,
 * - rozpoznania roli użytkownika,
 * - egzekwowania tenant boundary,
 * - sprawdzania reguł @PreAuthorize.
 *
 * To nie jest klient rezerwujący event.
 * Customer i AppUser pełnią różne role:
 *
 * - Customer reprezentuje osobę dokonującą rezerwacji,
 * - AppUser reprezentuje konto w systemie administracyjnym/security.
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    /**
     * Główny identyfikator użytkownika aplikacji.
     *
     * UUID jest generowany aplikacyjnie w konstruktorze.
     */
    @Id
    private UUID id;

    /**
     * Organizacja, do której należy użytkownik.
     *
     * Relacja ManyToOne oznacza:
     * - jeden użytkownik należy do jednej organizacji,
     * - jedna organizacja może mieć wielu użytkowników.
     *
     * Pole może być null, bo niektóre role techniczne albo globalne mogłyby nie być
     * przypisane do konkretnej organizacji.
     *
     * Jednak dla ról takich jak ORG_ADMIN, EVENT_MANAGER czy HR organizacja zwykle
     * powinna być wymagana, inaczej tenant boundary będzie trudne do egzekwowania.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    /**
     * Email użytkownika.
     *
     * nullable = false oznacza, że email jest wymagany.
     * unique = true oznacza, że w bazie nie powinno być dwóch użytkowników
     * z tym samym emailem.
     *
     * Email jest używany jako login w AuthService.
     */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * Hash hasła użytkownika.
     *
     * Nie przechowujemy hasła jawnie.
     *
     * To pole powinno zawierać wynik algorytmu typu BCrypt, Argon2 albo PBKDF2.
     *
     * W tym projekcie PasswordHasher ukrywa szczegóły hashowania.
     *
     * Pole może być null, bo wcześniejsze etapy projektu mogły tworzyć użytkowników
     * tylko do testów autoryzacji, bez pełnego flow logowania.
     * Produkcyjnie użytkownik logujący się hasłem powinien mieć passwordHash.
     */
    @Column(name = "password_hash")
    private String passwordHash;

    /**
     * Rola użytkownika w systemie.
     *
     * EnumType.STRING zapisuje nazwę roli jako tekst, np.:
     *
     * - CUSTOMER,
     * - EVENT_MANAGER,
     * - ORG_ADMIN,
     * - HR,
     * - SUPPORT.
     *
     * To jest bezpieczniejsze niż EnumType.ORDINAL, bo zmiana kolejności wartości
     * w enumie nie popsuje danych w bazie.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    /**
     * Moment utworzenia konta.
     *
     * updatable = false oznacza, że Hibernate nie powinien aktualizować tej kolumny
     * przy późniejszych zmianach encji.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Konstruktor bezargumentowy wymagany przez JPA.
     *
     * Jest protected, żeby kod aplikacyjny nie tworzył pustych, niepoprawnych
     * użytkowników.
     */
    protected AppUser() {
    }

    /**
     * Konstruktor pomocniczy dla użytkownika bez hasła.
     *
     * Użyteczny w testach i prostych scenariuszach autoryzacji.
     *
     * Deleguje do pełnego konstruktora, przekazując passwordHash = null.
     */
    public AppUser(Organization organization, String email, UserRole role) {
        this(organization, email, null, role);
    }

    /**
     * Główny konstruktor użytkownika aplikacji.
     *
     * Ustawia:
     * - UUID,
     * - organizację,
     * - email,
     * - hash hasła,
     * - rolę,
     * - czas utworzenia.
     */
    public AppUser(Organization organization, String email, String passwordHash, UserRole role) {
        this.id = UUID.randomUUID();
        this.organization = organization;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = Instant.now();
    }

    /**
     * Gettery używane przez:
     * - security,
     * - JwtTokenService,
     * - AuthService,
     * - komponenty autoryzacyjne,
     * - mapowanie DTO.
     *
     * Brak setterów jest celowy. W prostym modelu użytkownik jest tworzony
     * z konkretną rolą i organizacją.
     *
     * Jeśli w przyszłości będziesz chciał zmieniać email, hasło albo rolę,
     * lepiej dodać metody domenowe typu:
     *
     * changePasswordHash(...)
     * changeRole(...)
     * moveToOrganization(...)
     *
     * zamiast publicznych setterów dla wszystkich pól.
     */
    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}