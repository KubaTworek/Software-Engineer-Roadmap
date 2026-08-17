package com.example.paymentsystem.payout;

import com.example.paymentsystem.ledger.LedgerService;
import com.example.paymentsystem.merchant.Merchant;
import com.example.paymentsystem.merchant.MerchantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serwis odpowiedzialny za tworzenie payoutów dla merchantów.
 *
 * Payout to wypłata środków zgromadzonych na saldzie merchanta
 * na jego konto rozliczeniowe/bankowe.
 *
 * W tym systemie źródłem prawdy o dostępnych środkach nie jest tabela payments,
 * tylko ledger.
 *
 * Dlatego payout:
 * - pobiera saldo merchanta z ledgera,
 * - tworzy PayoutBatch,
 * - księguje wypłatę w ledgerze,
 * - oznacza payout jako PAID.
 */
@Service
public class PayoutService {

    /**
     * Repozytorium payoutów.
     *
     * Służy do zapisu batcha payoutu, czyli konkretnej wypłaty
     * dla danego merchanta.
     */
    private final PayoutBatchRepository repository;

    /**
     * Repozytorium merchantów.
     *
     * Potrzebne, żeby:
     * - sprawdzić, czy merchant istnieje,
     * - pobrać jego domyślną walutę settlementu.
     */
    private final MerchantRepository merchantRepository;

    /**
     * LedgerService jest kluczowy w payoutach.
     *
     * To z ledgera pobieramy saldo dostępne do wypłaty.
     * To ledger zapisuje też operację przeniesienia środków
     * z konta pending na konto bankowe merchanta.
     */
    private final LedgerService ledgerService;

    public PayoutService(
            PayoutBatchRepository repository,
            MerchantRepository merchantRepository,
            LedgerService ledgerService
    ) {
        this.repository = repository;
        this.merchantRepository = merchantRepository;
        this.ledgerService = ledgerService;
    }

    /**
     * Tworzy payout dla merchanta.
     *
     * Flow:
     * 1. Pobieramy merchanta.
     * 2. Ustalamy walutę payoutu.
     * 3. Pobieramy saldo merchanta z ledgera.
     * 4. Jeżeli saldo jest puste lub ujemne, blokujemy payout.
     * 5. Tworzymy PayoutBatch.
     * 6. Księgujemy payout w ledgerze.
     * 7. Oznaczamy payout jako PAID.
     *
     * @param merchantId ID merchanta, dla którego robimy wypłatę
     * @param currency opcjonalna waluta payoutu; jeżeli null, używamy settlementCurrency merchanta
     * @return utworzony i opłacony payout batch
     */
    @Transactional
    public PayoutBatch create(UUID merchantId, String currency) {

        /**
         * Pobieramy merchanta, żeby upewnić się, że istnieje
         * i żeby znać jego domyślną walutę settlementu.
         *
         * W produkcji warto rzucić własny wyjątek domenowy,
         * np. MerchantNotFoundException, zamiast surowego NoSuchElementException.
         */
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow();

        /**
         * Jeżeli request nie podał waluty payoutu,
         * używamy domyślnej waluty rozliczeniowej merchanta.
         *
         * Przykład:
         * merchant settlementCurrency = PLN
         * request currency = null
         * payoutCurrency = PLN
         */
        String payoutCurrency = currency == null
                ? merchant.getSettlementCurrency()
                : currency;

        /**
         * Pobieramy saldo z konta ledgerowego merchanta.
         *
         * Konto:
         * merchant:{merchantId}:pending
         *
         * To konto reprezentuje środki należne merchantowi,
         * które są dostępne do wypłaty.
         *
         * Przykład:
         * Po capture płatności 100,00 PLN i prowizji platformy 10%:
         *
         * CREDIT merchant:{merchantId}:pending 9000
         *
         * Saldo pending wynosi wtedy 9000, czyli 90,00 PLN.
         */
        long balance = ledgerService.balance(
                "merchant:" + merchantId + ":pending",
                payoutCurrency
        );

        /**
         * Jeżeli merchant nie ma dodatniego salda,
         * payout nie może zostać utworzony.
         *
         * To zabezpiecza przed:
         * - wypłatą pustego salda,
         * - wypłatą ujemnego salda,
         * - podwójną wypłatą tych samych środków.
         */
        if (balance <= 0) {
            throw new IllegalStateException("No available balance for payout");
        }

        /**
         * Tworzymy batch payoutu.
         *
         * Batch reprezentuje jedną operację wypłaty dla merchanta.
         * W tej uproszczonej wersji wypłacamy całe dostępne saldo.
         */
        PayoutBatch payout = repository.save(new PayoutBatch(
                merchantId,
                balance,
                payoutCurrency
        ));

        /**
         * Księgujemy payout w ledgerze.
         *
         * Przykład:
         *
         * DEBIT  merchant:{merchantId}:pending       9000
         * CREDIT merchant:{merchantId}:bank_account  9000
         *
         * Znaczenie:
         * - pending balance merchanta maleje,
         * - środki są oznaczone jako wypłacone na konto bankowe.
         */
        ledgerService.recordPayout(
                payout.getPayoutBatchId(),
                merchantId,
                balance,
                payoutCurrency
        );

        /**
         * Oznaczamy payout jako PAID.
         *
         * W realnym systemie payout raczej przechodziłby przez statusy:
         * - CREATED,
         * - PROCESSING,
         * - PAID,
         * - FAILED.
         *
         * Tutaj upraszczamy flow i zakładamy natychmiastową wypłatę.
         */
        payout.markPaid();

        return payout;
    }
}