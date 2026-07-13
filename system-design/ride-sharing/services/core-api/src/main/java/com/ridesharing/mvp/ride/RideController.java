package com.ridesharing.mvp.ride;

import com.ridesharing.mvp.auth.AuthenticatedUser;
import com.ridesharing.mvp.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Kontroler API dla pasażera w obszarze przejazdów.
 *
 * W aplikacji ride-sharing ta klasa obsługuje podstawowy flow pasażera:
 * - wycenę przejazdu,
 * - zamówienie przejazdu,
 * - pobranie statusu przejazdu,
 * - anulowanie przejazdu.
 *
 * Controller nie powinien zawierać logiki matchingu, płatności ani state machine.
 * Te decyzje należą do RideService i powiązanych serwisów domenowych.
 */
@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
public class RideController {

    /**
     * Główna usługa przejazdów.
     *
     * Odpowiada za:
     * - estymację trasy i ceny,
     * - utworzenie przejazdu,
     * - zmianę statusów,
     * - anulowanie,
     * - pobranie aktualnego stanu ride.
     */
    private final RideService rideService;

    /**
     * Serwis idempotencji.
     *
     * Chroni krytyczne operacje POST przed podwójnym wykonaniem,
     * np. gdy aplikacja mobilna ponowi request przez timeout albo słaby internet.
     */
    private final IdempotencyService idempotencyService;

    /**
     * Wycenia przejazd przed jego zamówieniem.
     *
     * Endpoint dostępny tylko dla pasażera.
     *
     * Flow:
     * 1. Pasażer wysyła pickup, dropoff i typ pojazdu.
     * 2. RideService liczy dystans/czas przez MapsClient.
     * 3. RideService wylicza szacowaną cenę.
     * 4. Frontend pokazuje pasażerowi estimate przed zamówieniem.
     *
     * To nie tworzy przejazdu. To tylko kalkulacja orientacyjna.
     */
    @PostMapping("/estimate")
    @PreAuthorize("hasRole('PASSENGER')")
    public EstimateResponse estimate(@Valid @RequestBody EstimateRequest request) {
        return rideService.estimate(request);
    }

    /**
     * Tworzy nowe zamówienie przejazdu.
     *
     * Endpoint dostępny tylko dla pasażera.
     *
     * To jedna z najbardziej krytycznych operacji w systemie, dlatego jest opakowana
     * w IdempotencyService. Bez tego retry requestu mogłoby utworzyć kilka przejazdów
     * dla tego samego pasażera.
     *
     * Header:
     * Idempotency-Key: unikalny klucz wygenerowany przez klienta dla tej próby zamówienia.
     *
     * Flow:
     * 1. Pobiera pasażera z tokenu.
     * 2. Przekazuje request do IdempotencyService.
     * 3. Jeżeli klucz był już użyty z tym samym body, zwraca poprzednią odpowiedź.
     * 4. Jeżeli to nowy request, wykonuje rideService.requestRide().
     * 5. RideService tworzy przejazd i uruchamia matching.
     */
    @PostMapping
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<RideDto> requestRide(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RideRequest request
    ) {
        var response = idempotencyService.execute(
                idempotencyKey,
                principal.user(),
                "POST /api/v1/rides",
                request,
                RideDto.class,
                () -> rideService.requestRide(principal.user(), request)
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Pobiera aktualny stan przejazdu.
     *
     * Ten endpoint jest używany przez aplikację pasażera do odświeżenia statusu,
     * szczególnie jako fallback, gdy WebSocket nie działa albo klient wraca po reconnect.
     *
     * RideService powinien sprawdzić, czy użytkownik ma prawo zobaczyć ten przejazd.
     * Obecna sygnatura nie przekazuje principal, więc kontrola dostępu musi być rozwiązana
     * inaczej albo wymaga poprawy.
     */
    @GetMapping("/{rideId}")
    public RideDto get(@PathVariable UUID rideId) {
        return rideService.get(rideId);
    }

    /**
     * Anuluje przejazd z perspektywy pasażera.
     *
     * Flow:
     * 1. Pobiera pasażera z tokenu.
     * 2. Przekazuje rideId i powód anulowania do RideService.
     * 3. RideService powinien sprawdzić ownership przejazdu i dozwolony status.
     * 4. RideService aktualizuje status, historię i ewentualne skutki uboczne,
     *    np. zwolnienie kierowcy albo naliczenie opłaty anulacyjnej.
     *
     * Jeżeli body nie zostało przesłane, system zapisuje powód "No reason".
     */
    @PostMapping("/{rideId}/cancel")
    public RideDto cancel(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID rideId,
            @RequestBody(required = false) CancelRequest request
    ) {
        return rideService.cancelByPassenger(
                principal.user(),
                rideId,
                request == null ? "No reason" : request.reason()
        );
    }

    /**
     * Punkt geograficzny używany w estimate i request ride.
     *
     * lat/lng mają walidację zakresu:
     * - latitude: od -90 do 90,
     * - longitude: od -180 do 180.
     *
     * address jest opcjonalnym opisem tekstowym dla UI, supportu albo historii przejazdu.
     */
    public record Point(
            @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
            String address
    ) {}

    /**
     * Request do wyceny przejazdu.
     *
     * pickup i dropoff określają trasę.
     * vehicleType pozwala różnicować cenę, np. standard, comfort, XL.
     */
    public record EstimateRequest(
            Point pickup,
            Point dropoff,
            String vehicleType
    ) {}

    /**
     * Odpowiedź z wyceną.
     *
     * estimatedPrice to cena orientacyjna, niekoniecznie finalna.
     * Finalna cena może się różnić po zakończeniu przejazdu,
     * zależnie od realnego czasu, dystansu, opłat i zasad pricingu.
     */
    public record EstimateResponse(
            BigDecimal estimatedPrice,
            String currency,
            double distanceKm,
            int durationMinutes
    ) {}

    /**
     * Request utworzenia przejazdu.
     *
     * paymentMethodId wskazuje metodę płatności pasażera.
     * W MVP może być mockowany, ale produkcyjnie musi wskazywać token/metodę
     * obsługiwaną przez Payment Service.
     */
    public record RideRequest(
            Point pickup,
            Point dropoff,
            String vehicleType,
            String paymentMethodId
    ) {}

    /**
     * Request anulowania przejazdu.
     *
     * reason jest wymagany, jeżeli body zostało podane.
     * Powód anulowania pomaga w supportcie, audycie i analizie cancellation rate.
     */
    public record CancelRequest(
            @NotBlank String reason
    ) {}
}