package com.example.paymentsystem.payout;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller odpowiedzialny za payouty merchantów.
 *
 * Payout to wypłata środków należnych merchantowi z platformy
 * na jego konto rozliczeniowe/bankowe.
 *
 * W kontekście Payment Systemu payout jest ostatnim etapem przepływu pieniędzy:
 *
 * 1. Klient płaci.
 * 2. System księguje capture w ledgerze.
 * 3. Część środków trafia na konto:
 *    merchant:{merchantId}:pending
 * 4. Payout przenosi środki z pending balance do wypłaty.
 *
 * Ten controller nie liczy salda samodzielnie.
 * Za logikę finansową odpowiada PayoutService i LedgerService.
 */
@RestController
@RequestMapping("/v1/merchants/{merchantId}/payouts")
public class PayoutController {

    /**
     * Serwis odpowiedzialny za utworzenie payoutu.
     *
     * To tutaj znajduje się właściwa logika:
     * - pobranie salda merchanta z ledgera,
     * - sprawdzenie, czy są środki do wypłaty,
     * - utworzenie batcha payoutu,
     * - zaksięgowanie wypłaty w ledgerze.
     */
    private final PayoutService service;

    /**
     * Repozytorium payout batchy.
     *
     * W tym controllerze używane do prostego odczytu historii payoutów
     * konkretnego merchanta.
     */
    private final PayoutBatchRepository repository;

    public PayoutController(
            PayoutService service,
            PayoutBatchRepository repository
    ) {
        this.service = service;
        this.repository = repository;
    }

    /**
     * Tworzy payout dla konkretnego merchanta.
     *
     * Flow:
     * 1. Pobieramy merchantId z URL.
     * 2. Opcjonalnie pobieramy walutę payoutu z request body.
     * 3. Jeżeli request body nie zostało przekazane, używamy domyślnej waluty
     *    settlementu merchanta w PayoutService.
     * 4. PayoutService sprawdza dostępne saldo merchanta.
     * 5. System tworzy PayoutBatch.
     * 6. Ledger księguje wypłatę.
     *
     * Przykład kont ledgerowych:
     *
     * DEBIT  merchant:{merchantId}:pending
     * CREDIT merchant:{merchantId}:bank_account
     *
     * Czyli środki przestają być dostępne jako pending balance
     * i są oznaczone jako wypłacone do merchanta.
     *
     * @param merchantId ID merchanta, dla którego tworzymy payout
     * @param request opcjonalne dane payoutu, np. waluta
     * @return utworzony payout batch
     */
    @PostMapping
    public PayoutBatch create(
            @PathVariable UUID merchantId,
            @RequestBody(required = false) CreatePayoutRequest request
    ) {
        return service.create(
                merchantId,
                request == null ? null : request.currency()
        );
    }

    /**
     * Zwraca historię payoutów konkretnego merchanta.
     *
     * Endpoint przydatny dla:
     * - panelu merchanta,
     * - panelu admina,
     * - debugowania rozliczeń,
     * - weryfikacji, czy środki zostały już wypłacone.
     *
     * W produkcyjnej wersji warto dodać:
     * - paginację,
     * - filtrowanie po statusie,
     * - zakres dat,
     * - autoryzację per merchant/admin.
     *
     * @param merchantId ID merchanta
     * @return lista payout batchy przypisanych do merchanta
     */
    @GetMapping
    public List<PayoutBatch> list(@PathVariable UUID merchantId) {
        return repository.findByMerchantId(merchantId);
    }
}