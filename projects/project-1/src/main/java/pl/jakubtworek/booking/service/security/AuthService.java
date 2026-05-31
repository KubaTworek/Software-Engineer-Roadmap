package pl.jakubtworek.booking.service.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.security.AuthResponse;
import pl.jakubtworek.booking.entity.AppUser;
import pl.jakubtworek.booking.entity.RefreshToken;
import pl.jakubtworek.booking.exception.BusinessRuleException;
import pl.jakubtworek.booking.repository.AppUserRepository;
import pl.jakubtworek.booking.repository.RefreshTokenRepository;
import pl.jakubtworek.booking.security.JwtProperties;
import pl.jakubtworek.booking.security.JwtTokenService;
import pl.jakubtworek.booking.security.PasswordHasher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Serwis odpowiedzialny za uwierzytelnianie użytkowników.
 *
 * Obsługuje:
 *
 * - login przez email i hasło,
 * - wydawanie JWT access tokenu,
 * - wydawanie refresh tokenu,
 * - zapisywanie hasha refresh tokenu w bazie,
 * - rotację refresh tokenów,
 * - unieważnianie starego refresh tokenu po użyciu.
 *
 * Ważne rozróżnienie:
 *
 * - access token jest krótko żyjącym tokenem używanym do autoryzacji requestów,
 * - refresh token jest dłużej żyjącym tokenem używanym do wydania nowego access tokenu.
 *
 * Refresh token powinien być traktowany jak sekret. Dlatego w bazie zapisujemy
 * jego hash, a nie surową wartość.
 */
@Service
public class AuthService {

    /**
     * Repozytorium użytkowników aplikacji.
     *
     * Używane do znalezienia użytkownika po emailu podczas logowania.
     */
    private final AppUserRepository appUserRepository;

    /**
     * Repozytorium refresh tokenów.
     *
     * Przechowuje hashe refresh tokenów, ich daty ważności i status aktywności.
     */
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Komponent odpowiedzialny za sprawdzanie hasła.
     *
     * Nazwa pola passwordEncoder jest historyczna — typ to PasswordHasher.
     * Lepsza nazwa pola byłaby passwordHasher, bo ten komponent ukrywa szczegóły
     * użytego algorytmu, np. BCrypt.
     */
    private final PasswordHasher passwordEncoder;

    /**
     * Serwis odpowiedzialny za tworzenie JWT access tokenów.
     *
     * AuthService nie powinien sam składać JWT. Deleguje to do JwtTokenService.
     */
    private final JwtTokenService jwtTokenService;

    /**
     * Konfiguracja czasów życia tokenów i innych parametrów JWT.
     */
    private final JwtProperties jwtProperties;

    /**
     * Bezpieczny generator losowości używany do refresh tokenów.
     *
     * Refresh token musi być nieprzewidywalny. Nie używamy Random.
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Constructor injection.
     *
     * Wszystkie zależności są jawne, a serwis łatwiej testować.
     */
    public AuthService(AppUserRepository appUserRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordHasher passwordEncoder,
                       JwtTokenService jwtTokenService,
                       JwtProperties jwtProperties) {
        this.appUserRepository = appUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Loguje użytkownika i wydaje nowe tokeny.
     *
     * Flow:
     *
     * 1. Znajdź użytkownika po emailu.
     * 2. Sprawdź hasło.
     * 3. Jeśli dane są poprawne, wydaj access token i refresh token.
     *
     * Metoda jest transakcyjna, bo issueTokens(...) zapisuje refresh token do bazy.
     */
    @Transactional
    public AuthResponse login(String email, String password) {
        /*
         * Przy nieistniejącym emailu zwracamy ogólny błąd "Invalid credentials".
         *
         * To jest dobre, bo nie ujawniamy, czy dany email istnieje w systemie.
         */
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleException("Invalid credentials"));

        /*
         * Jeśli użytkownik nie ma hasha hasła albo hasło nie pasuje,
         * również zwracamy ten sam ogólny błąd.
         *
         * Dzięki temu endpoint logowania nie zdradza szczegółów typu:
         * - użytkownik istnieje,
         * - ale nie ma ustawionego hasła,
         * - albo hasło jest błędne.
         */
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessRuleException("Invalid credentials");
        }

        return issueTokens(user);
    }

    /**
     * Odświeża tokeny na podstawie refresh tokenu.
     *
     * Flow:
     *
     * 1. Hashujemy surowy refresh token z requestu.
     * 2. Szukamy takiego hasha w bazie.
     * 3. Sprawdzamy, czy token jest aktywny.
     * 4. Unieważniamy obecny token.
     * 5. Wydajemy nowy access token i nowy refresh token.
     *
     * To jest rotacja refresh tokenów.
     *
     * Stary refresh token po użyciu nie powinien działać drugi raz.
     */
    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        /*
         * W bazie nie przechowujemy refresh tokenu w postaci jawnej.
         * Dlatego najpierw haszujemy wartość przesłaną przez klienta.
         */
        String hash = sha256(refreshTokenValue);

        RefreshToken current = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessRuleException("Invalid refresh token"));

        /*
         * Token musi być aktywny:
         * - nie może być wygasły,
         * - nie może być wcześniej unieważniony.
         */
        if (!current.isActive(Instant.now())) {
            throw new BusinessRuleException("Refresh token is expired or revoked");
        }

        /*
         * Rotacja: obecny refresh token zostaje unieważniony.
         *
         * Po tej operacji ten sam token nie powinien przejść ponownie.
         */
        current.revoke();

        return issueTokens(current.getUser());
    }

    /**
     * Wydaje parę tokenów:
     *
     * - access token JWT,
     * - refresh token.
     *
     * Access token jest zwracany klientowi i nie jest zapisywany w bazie.
     * Refresh token jest zwracany klientowi, ale w bazie zapisujemy tylko jego hash.
     */
    private AuthResponse issueTokens(AppUser user) {
        Instant now = Instant.now();

        /*
         * Tworzymy JWT access token z informacjami o użytkowniku.
         *
         * Typowo token zawiera subject/userId, role, organizationId i expiry.
         */
        String accessToken = jwtTokenService.createAccessToken(user, now);

        /*
         * Wyliczamy czas wygaśnięcia access tokenu.
         */
        Instant accessTokenExpiresAt = jwtTokenService.accessTokenExpiresAt(now);

        /*
         * Tworzymy losowy refresh token.
         *
         * Surowa wartość zostanie pokazana klientowi tylko raz.
         */
        String refreshTokenValue = randomRefreshToken();

        /*
         * Refresh token żyje dłużej niż access token.
         */
        Instant refreshTokenExpiresAt = now.plusSeconds(jwtProperties.refreshTokenSeconds());

        /*
         * W bazie zapisujemy tylko hash refresh tokenu.
         *
         * Dzięki temu wyciek bazy nie ujawnia bezpośrednio aktywnych refresh tokenów.
         */
        refreshTokenRepository.save(new RefreshToken(
                user,
                sha256(refreshTokenValue),
                refreshTokenExpiresAt
        ));

        return new AuthResponse(
                "Bearer",
                accessToken,
                accessTokenExpiresAt,
                refreshTokenValue,
                refreshTokenExpiresAt
        );
    }

    /**
     * Generuje losowy refresh token.
     *
     * Używamy 48 bajtów losowości, a następnie kodujemy je jako Base64 URL-safe
     * bez paddingu.
     *
     * Taki token:
     * - jest trudny do zgadnięcia,
     * - dobrze nadaje się do przesyłania w JSON/HTTP,
     * - nie zawiera znaków problematycznych dla URL.
     */
    private String randomRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /**
     * Liczy SHA-256 z podanej wartości.
     *
     * Używane do przechowywania refresh tokenów jako hash.
     *
     * Uwaga:
     * dla refresh tokenów SHA-256 jest akceptowalne, bo token ma wysoką entropię
     * i nie jest hasłem użytkownika.
     *
     * Dla haseł użytkowników nie używamy samego SHA-256.
     * Hasła powinny być hashowane algorytmem typu BCrypt, Argon2 albo PBKDF2.
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash refresh token", exception);
        }
    }
}