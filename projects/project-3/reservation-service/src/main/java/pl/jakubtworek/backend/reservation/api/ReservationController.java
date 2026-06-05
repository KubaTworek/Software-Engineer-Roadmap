package pl.jakubtworek.backend.reservation.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.backend.reservation.application.ReservationService;

import java.util.UUID;

/**
 * Kontroler HTTP dla Reservation Service.
 *
 * Odpowiada za wystawienie API związane z rezerwacjami:
 *
 * - utworzenie rezerwacji,
 * - pobranie rezerwacji,
 * - potwierdzenie rezerwacji,
 * - anulowanie rezerwacji.
 *
 * Kontroler powinien pozostać cienką warstwą transportową.
 * Nie powinien zawierać logiki biznesowej takiej jak:
 *
 * - sprawdzanie dostępności biletów,
 * - walidacja statusów rezerwacji,
 * - zmiana stanu rezerwacji,
 * - obsługa wygaśnięcia rezerwacji.
 *
 * Te decyzje należą do ReservationService.
 */
@RestController
@RequestMapping("/reservations")
public class ReservationController {

    /**
     * Warstwa aplikacyjna odpowiedzialna za przypadki użycia związane z rezerwacjami.
     */
    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    /**
     * Tworzy nową rezerwację dla wydarzenia.
     *
     * Przykład:
     *
     * POST /reservations
     *
     * {
     *   "eventId": "11111111-1111-1111-1111-111111111111",
     *   "userId": "user-123",
     *   "quantity": 2
     * }
     *
     * @Valid uruchamia walidację Bean Validation na CreateReservationRequest.
     * Jeśli request jest niepoprawny, Spring powinien zwrócić 400 Bad Request
     * zanim wywoła logikę aplikacyjną.
     */
    @PostMapping
    ReservationResponse create(@Valid @RequestBody CreateReservationRequest request) {
        return service.create(request);
    }

    /**
     * Pobiera rezerwację po ID.
     *
     * UUID jest automatycznie parsowany przez Springa.
     * Jeśli klient poda niepoprawny format UUID, request zakończy się błędem 400.
     *
     * Przykład:
     *
     * GET /reservations/22222222-2222-2222-2222-222222222222
     */
    @GetMapping("/{id}")
    ReservationResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    /**
     * Potwierdza rezerwację.
     *
     * Ten endpoint jest zwykle wywoływany przez Order Service po udanej płatności.
     *
     * Typowy flow:
     *
     * 1. Klient tworzy rezerwację.
     * 2. Klient tworzy zamówienie.
     * 3. Order Service wykonuje płatność.
     * 4. Order Service wywołuje POST /reservations/{id}/confirm.
     * 5. Reservation Service zmienia status rezerwacji z PENDING na CONFIRMED.
     *
     * Ten endpoint nie powinien być traktowany jako publiczna operacja użytkownika
     * bez dodatkowej autoryzacji w prawdziwym systemie.
     */
    @PostMapping("/{id}/confirm")
    ReservationResponse confirm(@PathVariable UUID id) {
        return service.confirm(id);
    }

    /**
     * Anuluje rezerwację.
     *
     * Może być użyte np. gdy:
     *
     * - użytkownik rezygnuje przed płatnością,
     * - rezerwacja wygasła,
     * - system wykonuje operację kompensacyjną.
     *
     * W poprawnej implementacji ReservationService powinien sprawdzić,
     * czy aktualny status rezerwacji pozwala na anulowanie.
     */
    @DeleteMapping("/{id}")
    ReservationResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}