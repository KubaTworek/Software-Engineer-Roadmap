package com.ridesharing.mvp.auth;

import com.ridesharing.mvp.common.ApiException;
import com.ridesharing.mvp.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za logikę rejestracji i logowania.
 *
 * W aplikacji ride-sharing AuthService jest miejscem, które tworzy konto użytkownika,
 * zabezpiecza hasło, sprawdza dane logowania i wydaje token JWT używany później
 * przy wywołaniach endpointów pasażera, kierowcy, admina i supportu.
 *
 * Controller tylko przyjmuje request. Faktyczna logika bezpieczeństwa jest tutaj.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * Repozytorium użytkowników.
     *
     * Służy do sprawdzania unikalności emaila i telefonu, zapisu nowego konta
     * oraz odczytu użytkownika podczas logowania.
     */
    private final AppUserRepository users;

    /**
     * Komponent Spring Security do hashowania i weryfikowania haseł.
     *
     * Hasła nigdy nie powinny być zapisywane w bazie jako plain text.
     * Do bazy trafia wyłącznie hash hasła.
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * Serwis generujący JWT.
     *
     * Token zwrócony po rejestracji/logowaniu jest później używany w nagłówku:
     * Authorization: Bearer <token>
     */
    private final JwtService jwtService;

    /**
     * Rejestruje nowego użytkownika.
     *
     * Cała metoda jest transakcyjna, ponieważ tworzy nowy rekord użytkownika.
     * Jeżeli zapis się nie powiedzie, transakcja zostanie wycofana.
     *
     * Flow:
     * 1. Sprawdza, czy email nie jest już zajęty.
     * 2. Sprawdza, czy numer telefonu nie jest już zajęty.
     * 3. Tworzy nowego użytkownika z domyślnym statusem ACTIVE i ratingiem 5.00.
     * 4. Hashuje hasło.
     * 5. Zapisuje użytkownika w bazie.
     * 6. Zwraca JWT i podstawowe dane użytkownika.
     */
    @Transactional
    public AuthController.AuthResponse register(AuthController.RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already exists");
        }

        if (users.existsByPhoneNumber(request.phoneNumber())) {
            throw new ApiException(HttpStatus.CONFLICT, "Phone number already exists");
        }

        var now = Instant.now();

        var user = AppUser.builder()
                .id(UUID.randomUUID())
                .email(request.email())
                .phoneNumber(request.phoneNumber())

                /*
                 * Hasło jest hashowane przed zapisem.
                 * To kluczowe: nawet przy wycieku bazy atakujący nie powinien dostać surowych haseł.
                 */
                .passwordHash(passwordEncoder.encode(request.password()))

                .fullName(request.fullName())
                .role(request.role())

                /*
                 * Konto od razu jest aktywne.
                 * W produkcji można tu dodać status PENDING_VERIFICATION,
                 * np. do potwierdzenia emaila, telefonu albo dokumentów kierowcy.
                 */
                .status(UserStatus.ACTIVE)

                /*
                 * Nowy użytkownik startuje z neutralnym/maksymalnym ratingiem.
                 * W ride-sharingu rating wpływa później na zaufanie, matching i moderację.
                 */
                .rating(BigDecimal.valueOf(5.00))

                .createdAt(now)
                .updatedAt(now)
                .build();

        users.save(user);

        /*
         * Po rejestracji użytkownik od razu dostaje token.
         * Dzięki temu frontend nie musi wykonywać osobnego loginu po register.
         */
        return response(user);
    }

    /**
     * Loguje istniejącego użytkownika.
     *
     * Flow:
     * 1. Szuka użytkownika po emailu.
     * 2. Jeżeli użytkownik nie istnieje, zwraca ogólny błąd "Invalid credentials".
     * 3. Porównuje hasło z hashem zapisanym w bazie.
     * 4. Jeżeli dane są poprawne, zwraca JWT.
     *
     * Komunikat błędu jest celowo taki sam dla nieistniejącego emaila i złego hasła.
     * To ogranicza enumerację kont.
     */
    public AuthController.AuthResponse login(AuthController.LoginRequest request) {
        var user = users.findByEmail(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        return response(user);
    }

    /**
     * Buduje odpowiedź auth na podstawie użytkownika.
     *
     * Odpowiedź zawiera:
     * - accessToken: JWT do autoryzacji kolejnych requestów,
     * - tokenType: zwykle Bearer,
     * - userId: identyfikator użytkownika dla frontendu,
     * - role: rola potrzebna do routingu UI i kontroli dostępu.
     */
    private AuthController.AuthResponse response(AppUser user) {
        return new AuthController.AuthResponse(
                jwtService.generate(user),
                "Bearer",
                user.getId().toString(),
                user.getRole().name()
        );
    }
}