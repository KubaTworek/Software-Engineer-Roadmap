package com.example.paymentsystem.ledger;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller do podglądu ledgera.
 *
 * Ledger to finansowe źródło prawdy o przepływie pieniędzy.
 * Status płatności mówi, co stało się z paymentem,
 * ale ledger pokazuje, jak zmieniły się salda kont.
 *
 * Ten controller jest głównie operacyjno-diagnostyczny:
 * - pozwala sprawdzić wpisy konkretnej transakcji ledgerowej,
 * - pozwala sprawdzić saldo danego konta.
 *
 * Nie tworzy wpisów ledgerowych.
 * Księgowanie odbywa się w serwisach domenowych, np. PaymentService,
 * ChargebackService albo PayoutService.
 */
@RestController
@RequestMapping("/v1/ledger")
public class LedgerController {

    /**
     * Repozytorium wpisów ledgerowych.
     *
     * Używane tutaj tylko do odczytu wpisów powiązanych
     * z konkretną transakcją ledgerową.
     */
    private final LedgerEntryRepository repository;

    /**
     * Serwis ledgerowy.
     *
     * W tym controllerze używany do policzenia salda konta.
     * Sama logika bilansowania i zapisu double-entry znajduje się w LedgerService.
     */
    private final LedgerService ledgerService;

    public LedgerController(
            LedgerEntryRepository repository,
            LedgerService ledgerService
    ) {
        this.repository = repository;
        this.ledgerService = ledgerService;
    }

    /**
     * Zwraca wszystkie wpisy ledgerowe dla konkretnej transakcji.
     *
     * Jedna transakcja ledgerowa składa się z kilku wpisów.
     * W double-entry suma DEBIT powinna równać się sumie CREDIT.
     *
     * Przykład dla udanej płatności marketplace:
     *
     * DEBIT  external_psp_clearing          10000
     * CREDIT merchant:{merchantId}:pending   9000
     * CREDIT platform:fee_revenue            1000
     *
     * Ten endpoint pozwala sprawdzić, czy dana operacja została
     * poprawnie zaksięgowana.
     *
     * @param transactionId ID transakcji ledgerowej
     * @return lista wpisów ledgerowych w formie DTO
     */
    @GetMapping("/transactions/{transactionId}/entries")
    public List<LedgerEntryResponse> entries(@PathVariable UUID transactionId) {
        return repository.findByTransactionId(transactionId)
                .stream()
                .map(LedgerEntryResponse::from)
                .toList();
    }

    /**
     * Zwraca saldo konkretnego konta ledgerowego w danej walucie.
     *
     * Konto ledgerowe to logiczny identyfikator, np.:
     * - external_psp_clearing,
     * - platform:fee_revenue,
     * - merchant:{merchantId}:pending,
     * - merchant:{merchantId}:bank_account.
     *
     * Saldo jest liczone na podstawie wpisów:
     * - CREDIT zwiększa saldo,
     * - DEBIT zmniejsza saldo.
     *
     * Przykład:
     *
     * GET /v1/ledger/accounts/merchant:abc:pending/balance?currency=PLN
     *
     * pozwala sprawdzić, ile środków merchant ma aktualnie dostępnych
     * do payoutu w PLN.
     *
     * @param accountId identyfikator konta ledgerowego
     * @param currency waluta salda, np. PLN, EUR, USD
     * @return saldo konta w najmniejszej jednostce waluty
     */
    @GetMapping("/accounts/{accountId}/balance")
    public long balance(
            @PathVariable String accountId,
            @RequestParam String currency
    ) {
        return ledgerService.balance(accountId, currency);
    }
}