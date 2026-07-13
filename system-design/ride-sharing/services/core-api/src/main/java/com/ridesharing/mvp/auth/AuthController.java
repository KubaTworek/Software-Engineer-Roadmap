package com.ridesharing.mvp.auth;

import com.ridesharing.mvp.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Publiczny kontroler odpowiedzialny za uwierzytelnianie użytkowników.
 *
 * W kontekście aplikacji ride-sharing to wejściowy punkt dla pasażerów,
 * kierowców i administratorów, którzy chcą założyć konto albo zalogować się
 * do systemu.
 *
 * Ten controller nie powinien zawierać logiki rejestracji, hashowania haseł
 * ani generowania tokenów. Jego rola to przyjęcie requestu, walidacja danych
 * wejściowych i delegacja operacji do AuthService.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * Serwis domenowy odpowiedzialny za właściwą logikę auth:
     * - tworzenie użytkownika,
     * - hashowanie hasła,
     * - sprawdzanie danych logowania,
     * - generowanie JWT,
     * - ustawienie roli użytkownika.
     */
    private final AuthService authService;

    /**
     * Rejestruje nowego użytkownika w systemie.
     *
     * Endpoint obsługuje różne typy kont przez pole role, np. PASSENGER, DRIVER albo ADMIN.
     * W realnej produkcji rejestracja ADMIN-a nie powinna być publicznie dostępna —
     * konto admina powinno być tworzone przez seed, panel wewnętrzny albo osobny proces.
     *
     * @Valid uruchamia walidację pól z RegisterRequest:
     * - poprawny email,
     * - wymagany numer telefonu,
     * - minimalna długość hasła,
     * - wymagane imię i nazwisko,
     * - wymagana rola.
     */
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * Loguje użytkownika i zwraca token dostępowy.
     *
     * AuthService powinien:
     * - znaleźć użytkownika po emailu,
     * - porównać hasło z hashem w bazie,
     * - odrzucić konto zablokowane lub nieaktywne,
     * - wygenerować token JWT z userId i rolą.
     *
     * Controller nie powinien zdradzać, czy błędny był email czy hasło,
     * żeby nie ułatwiać enumeracji kont.
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Dane wymagane do utworzenia konta.
     *
     * email jest głównym identyfikatorem logowania.
     * phoneNumber jest istotny operacyjnie w ride-sharingu, np. do kontaktu,
     * weryfikacji konta lub obsługi supportu.
     * role określa, jaki typ użytkownika powstaje w systemie.
     */
    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank String phoneNumber,
            @Size(min = 8) String password,
            @NotBlank String fullName,
            @NotNull UserRole role
    ) {}

    /**
     * Dane logowania.
     *
     * W tym MVP logowanie odbywa się po emailu i haśle.
     * Docelowo można dodać MFA, logowanie telefonem, refresh tokeny
     * oraz blokadę po wielu nieudanych próbach.
     */
    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    /**
     * Odpowiedź po poprawnej rejestracji albo logowaniu.
     *
     * accessToken to token używany potem w nagłówku:
     * Authorization: Bearer <token>
     *
     * tokenType zwykle ma wartość Bearer.
     * userId i role pozwalają frontendowi od razu zdecydować,
     * czy przekierować użytkownika do widoku pasażera, kierowcy czy admina.
     */
    public record AuthResponse(
            String accessToken,
            String tokenType,
            String userId,
            String role
    ) {}
}