package com.example.paymentsystem.chargeback;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller odpowiedzialny za obsługę chargebacków.
 *
 * Chargeback to spór płatniczy zainicjowany zwykle przez klienta w banku/PSP.
 * Nie jest tym samym co refund:
 * - refund jest kontrolowaną operacją zwrotu po stronie naszej aplikacji,
 * - chargeback jest zewnętrznym sporem, który może obciążyć merchanta.
 *
 * Controller nie zawiera logiki finansowej.
 * Jego zadaniem jest przyjęcie requestu HTTP, wyciągnięcie danych z URL/body
 * i przekazanie operacji do ChargebackService.
 */
@RestController
@RequestMapping("/v1")
public class ChargebackController {

    /**
     * Serwis domenowy wykonujący właściwą logikę chargebacków:
     * - zmianę statusu płatności,
     * - zapis chargebacku,
     * - księgowanie skutków w ledgerze,
     * - walidację dozwolonych przejść stanu.
     */
    private final ChargebackService service;

    public ChargebackController(ChargebackService service) {
        this.service = service;
    }

    /**
     * Otwiera chargeback dla istniejącej płatności.
     *
     * Endpoint reprezentuje moment, w którym system dowiaduje się,
     * że dla płatności powstał spór, np. klient zgłosił transakcję jako fraud.
     *
     * Kluczowe skutki:
     * - tworzony jest rekord Chargeback,
     * - płatność przechodzi w stan CHARGEBACK_OPENED,
     * - system zaczyna traktować środki jako sporne.
     *
     * @param paymentId ID płatności, której dotyczy chargeback
     * @param request dane chargebacku, np. kwota i powód
     * @return utworzony chargeback
     */
    @PostMapping("/payments/{paymentId}/chargebacks")
    public Chargeback open(
            @PathVariable UUID paymentId,
            @Valid @RequestBody OpenChargebackRequest request
    ) {
        return service.open(paymentId, request);
    }

    /**
     * Oznacza chargeback jako przegrany.
     *
     * To najważniejszy finansowo scenariusz chargebacku.
     * Jeżeli merchant/platforma przegrywa spór, środki są odbierane
     * i trzeba to odzwierciedlić w ledgerze.
     *
     * Kluczowe skutki:
     * - status chargebacku zmienia się na LOST,
     * - status płatności zmienia się na CHARGEBACK_LOST,
     * - ledger księguje obciążenie merchanta.
     *
     * @param chargebackId ID chargebacku
     * @return zaktualizowany chargeback
     */
    @PostMapping("/chargebacks/{chargebackId}/lose")
    public Chargeback lose(@PathVariable UUID chargebackId) {
        return service.lose(chargebackId);
    }

    /**
     * Oznacza chargeback jako wygrany.
     *
     * Ten scenariusz oznacza, że spór został rozstrzygnięty na korzyść
     * merchanta/platformy. W uproszczonej implementacji aktualizujemy statusy,
     * ale nie wykonujemy dodatkowego księgowania środków.
     *
     * Kluczowe skutki:
     * - status chargebacku zmienia się na WON,
     * - status płatności zmienia się na CHARGEBACK_WON.
     *
     * @param chargebackId ID chargebacku
     * @return zaktualizowany chargeback
     */
    @PostMapping("/chargebacks/{chargebackId}/win")
    public Chargeback win(@PathVariable UUID chargebackId) {
        return service.win(chargebackId);
    }
}