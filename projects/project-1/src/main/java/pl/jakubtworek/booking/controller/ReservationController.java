package pl.jakubtworek.booking.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.service.ReservationService;
import pl.jakubtworek.booking.service.async.AsyncReservationService;
import pl.jakubtworek.booking.service.async.PaymentScenario;

import java.util.UUID;

/**
 * Kontroler REST odpowiedzialny za operacje HTTP związane z rezerwacjami.
 *
 * W klasycznym monolicie warstwowym kontroler powinien pełnić rolę cienkiej
 * warstwy wejściowej:
 *
 * - odbiera request HTTP,
 * - mapuje parametry URL/body na obiekty Javy,
 * - uruchamia walidację DTO,
 * - deleguje logikę do serwisów,
 * - zwraca DTO jako odpowiedź.
 *
 * Kontroler nie powinien zawierać logiki biznesowej, transakcyjnej ani
 * bezpośredniego dostępu do repozytoriów.
 */
@RestController
@RequestMapping("/api")
public class ReservationController {

    /**
     * Główny serwis rezerwacji.
     *
     * Odpowiada za klasyczny, synchroniczny flow:
     * - utworzenie rezerwacji,
     * - potwierdzenie rezerwacji,
     * - anulowanie rezerwacji,
     * - pobranie rezerwacji.
     */
    private final ReservationService reservationService;

    /**
     * Serwis dodany w etapie asynchroniczności.
     *
     * Odpowiada za flow, w którym potwierdzenie rezerwacji jest powiązane
     * z operacjami, które nie powinny być wykonywane bezpośrednio w prostym
     * kodzie kontrolera:
     *
     * - walidacja płatności,
     * - timeout płatności,
     * - fallback,
     * - wysyłka maila,
     * - zapis audytu,
     * - powiadomienie systemu zewnętrznego.
     */
    private final AsyncReservationService asyncReservationService;

    /**
     * Constructor injection.
     *
     * Dzięki temu zależności są jawne, pola mogą być final,
     * a kontroler jest łatwiejszy do przetestowania.
     */
    public ReservationController(ReservationService reservationService,
                                 AsyncReservationService asyncReservationService) {
        this.reservationService = reservationService;
        this.asyncReservationService = asyncReservationService;
    }

    /**
     * Tworzy rezerwację dla konkretnego eventu.
     *
     * Endpoint:
     *
     * POST /api/events/{eventId}/reservations
     *
     * eventId pochodzi ze ścieżki URL, a dane rezerwacji z body requestu.
     *
     * @Valid uruchamia walidację DTO ReservationCreateRequest.
     * Jeśli request jest niepoprawny, Spring powinien zwrócić HTTP 400,
     * zwykle przez globalny exception handler.
     *
     * @ResponseStatus(HttpStatus.CREATED) oznacza, że poprawne utworzenie
     * rezerwacji zwraca HTTP 201.
     */
    @PostMapping("/events/{eventId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@PathVariable UUID eventId,
                                      @Valid @RequestBody ReservationCreateRequest request) {
        return reservationService.create(eventId, request);
    }

    /**
     * Synchronicznie potwierdza rezerwację.
     *
     * Endpoint:
     *
     * POST /api/reservations/{reservationId}/confirm
     *
     * Ten wariant używa podstawowego ReservationService i jest prostszy:
     * request kończy się dopiero po wykonaniu całej logiki potwierdzenia.
     *
     * Jeśli rezerwacja nie istnieje, powinna zostać zwrócona odpowiedź 404.
     * Jeśli rezerwacja jest w stanie, którego nie można potwierdzić,
     * powinna zostać zwrócona odpowiedź konfliktu, np. HTTP 409.
     */
    @PostMapping("/reservations/{reservationId}/confirm")
    public ReservationResponse confirm(@PathVariable UUID reservationId) {
        return reservationService.confirm(reservationId);
    }

    /**
     * Potwierdza rezerwację przez flow asynchroniczny.
     *
     * Endpoint:
     *
     * POST /api/reservations/{reservationId}/confirm-async?paymentScenario=APPROVED
     *
     * paymentScenario pozwala testować różne zachowania zewnętrznego payment providera:
     *
     * - APPROVED — płatność zaakceptowana,
     * - DECLINED — płatność odrzucona,
     * - SLOW — provider odpowiada zbyt wolno i powinien zadziałać timeout,
     * - FAILING — provider kończy się błędem i powinien zadziałać fallback.
     *
     * asyncReservationService.confirm(...) zwraca CompletableFuture.
     *
     * Wywołanie .join() sprawia, że kontroler czeka na wynik.
     * To oznacza, że operacje wewnętrzne są asynchroniczne względem siebie,
     * ale sam request HTTP nadal jest blokujący.
     *
     * Jest to akceptowalne jako ćwiczenie do nauki CompletableFuture,
     * timeoutów, fallbacków i propagacji wyjątków.
     *
     * W produkcyjnym API warto rozważyć alternatywę:
     * - zwrócić HTTP 202 Accepted,
     * - uruchomić proces w tle,
     * - pozwolić klientowi odpytać status rezerwacji osobnym endpointem.
     */
    @PostMapping("/reservations/{reservationId}/confirm-async")
    public ReservationResponse confirmAsync(
            @PathVariable UUID reservationId,
            @RequestParam(defaultValue = "APPROVED") PaymentScenario paymentScenario
    ) {
        return asyncReservationService.confirm(reservationId, paymentScenario).join();
    }

    /**
     * Anuluje rezerwację.
     *
     * Endpoint:
     *
     * POST /api/reservations/{reservationId}/cancel
     *
     * W podstawowym MVP anulowanie rezerwacji powinno:
     * - zmienić status rezerwacji,
     * - zwolnić miejsce w puli, jeśli rezerwacja wcześniej blokowała dostępność,
     * - nie pozwolić na anulowanie rezerwacji w niepoprawnym stanie.
     */
    @PostMapping("/reservations/{reservationId}/cancel")
    public ReservationResponse cancel(@PathVariable UUID reservationId) {
        return reservationService.cancel(reservationId);
    }

    /**
     * Pobiera szczegóły pojedynczej rezerwacji.
     *
     * Endpoint:
     *
     * GET /api/reservations/{reservationId}
     *
     * UUID w ścieżce zostanie automatycznie sparsowany przez Springa.
     * Błędny format UUID powinien skutkować HTTP 400.
     *
     * Brak rezerwacji o podanym ID powinien zostać zmapowany na HTTP 404.
     */
    @GetMapping("/reservations/{reservationId}")
    public ReservationResponse get(@PathVariable UUID reservationId) {
        return reservationService.get(reservationId);
    }
}