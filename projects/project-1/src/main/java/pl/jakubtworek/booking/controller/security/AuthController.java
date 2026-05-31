package pl.jakubtworek.booking.controller.security;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.booking.dto.security.AuthResponse;
import pl.jakubtworek.booking.dto.security.LoginRequest;
import pl.jakubtworek.booking.dto.security.RefreshRequest;
import pl.jakubtworek.booking.service.security.AuthService;

/**
 * Kontroler REST odpowiedzialny za uwierzytelnianie.
 *
 * Ten kontroler obsługuje dwa podstawowe przypadki:
 *
 * - logowanie użytkownika,
 * - odświeżanie tokenów przez refresh token.
 *
 * Ważne rozróżnienie:
 *
 * - authentication, czyli uwierzytelnienie, odpowiada na pytanie:
 *   "kim jesteś?"
 *
 * - authorization, czyli autoryzacja, odpowiada na pytanie:
 *   "czy wolno ci wykonać tę operację?"
 *
 * Ten kontroler dotyczy głównie authentication.
 * Reguły authorization znajdują się później przy endpointach secure
 * oraz w komponentach używanych przez @PreAuthorize.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Serwis odpowiedzialny za logikę logowania, generowania tokenów
     * i rotacji refresh tokenów.
     *
     * Kontroler nie powinien sam:
     *
     * - sprawdzać hasła,
     * - generować JWT,
     * - zapisywać refresh tokenów,
     * - zarządzać rotacją tokenów.
     *
     * To należy do warstwy serwisowej.
     */
    private final AuthService authService;

    /**
     * Constructor injection.
     *
     * Zależność jest jawna, pole może być final, a kontroler jest prosty
     * do testowania.
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Loguje użytkownika.
     *
     * Endpoint:
     *
     * POST /api/auth/login
     *
     * Body requestu zawiera zwykle:
     *
     * - email,
     * - password.
     *
     * @Valid uruchamia walidację LoginRequest.
     * Jeśli email albo hasło są puste lub niepoprawne, Spring powinien zwrócić
     * HTTP 400 przez mechanizm walidacji i globalny exception handler.
     *
     * Jeśli dane logowania są poprawne, serwis zwraca AuthResponse,
     * czyli zwykle:
     *
     * - access token,
     * - refresh token,
     * - informacje o użytkowniku/rolach,
     * - czas wygaśnięcia tokenu.
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    /**
     * Odświeża access token na podstawie refresh tokenu.
     *
     * Endpoint:
     *
     * POST /api/auth/refresh
     *
     * Body requestu zawiera refreshToken.
     *
     * W projekcie Stage 7 refresh token powinien być rotowany:
     *
     * - klient wysyła aktualny refresh token,
     * - serwis sprawdza, czy token istnieje i nie został zużyty,
     * - stary refresh token zostaje unieważniony,
     * - serwis wydaje nowy access token i nowy refresh token.
     *
     * Dzięki rotacji stary refresh token nie powinien działać drugi raz.
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }
}