package com.example.paymentsystem.ledger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za księgowanie operacji finansowych w ledgerze.
 *
 * Ledger jest finansowym źródłem prawdy w systemie.
 * Status płatności mówi, co stało się z paymentem,
 * ale ledger mówi, jak zmieniły się salda kont.
 *
 * Ten serwis używa modelu double-entry accounting:
 * - każda operacja musi mieć stronę DEBIT,
 * - każda operacja musi mieć stronę CREDIT,
 * - suma DEBIT musi być równa sumie CREDIT.
 *
 * Dzięki temu system nie tworzy "pieniędzy z powietrza"
 * i nie gubi środków przy capture, refundach, chargebackach oraz payoutach.
 */
@Service
public class LedgerService {

    /**
     * Repozytorium transakcji ledgerowych.
     *
     * LedgerTransaction reprezentuje jedną operację biznesową,
     * np. capture płatności, refund, chargeback loss albo payout.
     *
     * Do jednej transakcji przypisanych jest wiele wpisów LedgerEntry.
     */
    private final LedgerTransactionRepository transactionRepository;

    /**
     * Repozytorium pojedynczych wpisów ledgerowych.
     *
     * LedgerEntry reprezentuje pojedynczy ruch na konkretnym koncie:
     * - DEBIT,
     * - CREDIT,
     * - amount,
     * - currency.
     */
    private final LedgerEntryRepository entryRepository;

    public LedgerService(
            LedgerTransactionRepository transactionRepository,
            LedgerEntryRepository entryRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
    }

    /**
     * Księguje udaną płatność marketplace.
     *
     * To jest moment, w którym środki zostały skutecznie pobrane
     * od klienta przez PSP.
     *
     * Przykład dla płatności 10000 PLN:
     *
     * DEBIT  external_psp_clearing             10000
     * CREDIT merchant:{merchantId}:pending      9000
     * CREDIT platform:fee_revenue               1000
     *
     * Znaczenie:
     * - external_psp_clearing pokazuje środki po stronie PSP,
     * - merchant pending balance pokazuje należność merchanta,
     * - platform fee revenue pokazuje prowizję platformy.
     *
     * Ta metoda oddziela kwotę merchanta od prowizji platformy.
     */
    @Transactional
    public void recordMarketplaceCapture(
            UUID paymentId,
            UUID merchantId,
            long amount,
            long platformFee,
            long merchantAmount,
            String currency
    ) {
        record("PAYMENT", paymentId, "MARKETPLACE_CAPTURE", List.of(
                new Line(
                        "external_psp_clearing",
                        LedgerDirection.DEBIT,
                        amount,
                        currency
                ),
                new Line(
                        "merchant:" + merchantId + ":pending",
                        LedgerDirection.CREDIT,
                        merchantAmount,
                        currency
                ),
                new Line(
                        "platform:fee_revenue",
                        LedgerDirection.CREDIT,
                        platformFee,
                        currency
                )
        ));
    }

    /**
     * Księguje refund.
     *
     * Refund odwraca część wcześniejszego capture.
     *
     * Przykład refundu 2500 PLN:
     *
     * DEBIT  merchant:{merchantId}:pending   2500
     * CREDIT external_psp_clearing           2500
     *
     * Znaczenie:
     * - zmniejszamy saldo merchanta,
     * - środki wracają przez PSP do klienta.
     *
     * W tej uproszczonej wersji refund obciąża konto merchanta.
     * W bardziej rozbudowanej wersji można rozdzielić zwrot prowizji platformy,
     * opłaty PSP i częściowy split fee.
     */
    @Transactional
    public void recordRefund(
            UUID refundId,
            UUID merchantId,
            long amount,
            String currency
    ) {
        record("REFUND", refundId, "REFUND", List.of(
                new Line(
                        "merchant:" + merchantId + ":pending",
                        LedgerDirection.DEBIT,
                        amount,
                        currency
                ),
                new Line(
                        "external_psp_clearing",
                        LedgerDirection.CREDIT,
                        amount,
                        currency
                )
        ));
    }

    /**
     * Księguje przegrany chargeback.
     *
     * Chargeback loss oznacza, że spór został rozstrzygnięty przeciwko
     * merchantowi/platformie i środki muszą zostać odebrane.
     *
     * Przykład:
     *
     * DEBIT  merchant:{merchantId}:pending   10000
     * CREDIT external_psp_clearing           10000
     *
     * Znaczenie:
     * - merchant traci środki,
     * - clearing PSP pokazuje odpływ środków w wyniku chargebacku.
     *
     * To jest finansowo podobne do refundu, ale biznesowo oznacza spór,
     * a nie dobrowolny zwrot.
     */
    @Transactional
    public void recordChargebackLoss(
            UUID chargebackId,
            UUID merchantId,
            long amount,
            String currency
    ) {
        record("CHARGEBACK", chargebackId, "CHARGEBACK_LOST", List.of(
                new Line(
                        "merchant:" + merchantId + ":pending",
                        LedgerDirection.DEBIT,
                        amount,
                        currency
                ),
                new Line(
                        "external_psp_clearing",
                        LedgerDirection.CREDIT,
                        amount,
                        currency
                )
        ));
    }

    /**
     * Księguje payout do merchanta.
     *
     * Payout przenosi środki z salda pending merchanta
     * na jego konto bankowe.
     *
     * Przykład:
     *
     * DEBIT  merchant:{merchantId}:pending       9000
     * CREDIT merchant:{merchantId}:bank_account  9000
     *
     * Znaczenie:
     * - zmniejszamy środki dostępne do wypłaty,
     * - oznaczamy, że środki zostały wypłacone na konto bankowe merchanta.
     *
     * W realnym systemie payout miałby jeszcze statusy typu:
     * CREATED, PROCESSING, PAID, FAILED.
     */
    @Transactional
    public void recordPayout(
            UUID payoutBatchId,
            UUID merchantId,
            long amount,
            String currency
    ) {
        record("PAYOUT", payoutBatchId, "MERCHANT_PAYOUT", List.of(
                new Line(
                        "merchant:" + merchantId + ":pending",
                        LedgerDirection.DEBIT,
                        amount,
                        currency
                ),
                new Line(
                        "merchant:" + merchantId + ":bank_account",
                        LedgerDirection.CREDIT,
                        amount,
                        currency
                )
        ));
    }

    /**
     * Zwraca saldo konkretnego konta ledgerowego w danej walucie.
     *
     * Przykładowe konta:
     * - external_psp_clearing,
     * - platform:fee_revenue,
     * - merchant:{merchantId}:pending,
     * - merchant:{merchantId}:bank_account.
     *
     * Saldo jest liczone w repozytorium na podstawie wpisów:
     * - CREDIT zwiększa saldo,
     * - DEBIT zmniejsza saldo.
     *
     * To jest używane np. przy payoutach,
     * żeby sprawdzić, ile merchant ma dostępne do wypłaty.
     */
    public long balance(String accountId, String currency) {
        return entryRepository.balance(accountId, currency);
    }

    /**
     * Wspólna metoda zapisująca transakcję ledgerową.
     *
     * Odpowiada za cztery kluczowe rzeczy:
     *
     * 1. Idempotencję księgowania.
     *    Jeżeli transakcja o tym samym:
     *    - referenceType,
     *    - referenceId,
     *    - transactionType
     *    już istnieje, metoda nic nie robi.
     *
     *    Dzięki temu ponowne przetworzenie tego samego eventu
     *    nie zaksięguje pieniędzy drugi raz.
     *
     * 2. Walidację double-entry.
     *    Suma DEBIT musi być równa sumie CREDIT.
     *
     * 3. Zapis LedgerTransaction.
     *    To logiczna transakcja finansowa, np. MARKETPLACE_CAPTURE.
     *
     * 4. Zapis LedgerEntry.
     *    To konkretne ruchy na kontach.
     */
    private void record(
            String referenceType,
            UUID referenceId,
            String transactionType,
            List<Line> lines
    ) {
        /**
         * Idempotencja ledgerowa.
         *
         * Jeżeli ta sama operacja biznesowa była już zaksięgowana,
         * nie zapisujemy jej drugi raz.
         *
         * To chroni przed:
         * - powtórzonym webhookiem,
         * - retry joba,
         * - ponownym kliknięciem w panelu admina,
         * - replayem eventu z kolejki.
         */
        if (transactionRepository
                .findByReferenceTypeAndReferenceIdAndTransactionType(
                        referenceType,
                        referenceId,
                        transactionType
                )
                .isPresent()) {
            return;
        }

        /**
         * Liczymy sumę wszystkich wpisów DEBIT.
         */
        long debit = lines.stream()
                .filter(l -> l.direction() == LedgerDirection.DEBIT)
                .mapToLong(Line::amount)
                .sum();

        /**
         * Liczymy sumę wszystkich wpisów CREDIT.
         */
        long credit = lines.stream()
                .filter(l -> l.direction() == LedgerDirection.CREDIT)
                .mapToLong(Line::amount)
                .sum();

        /**
         * Najważniejsza walidacja w ledgerze:
         *
         * suma DEBIT == suma CREDIT
         *
         * Jeżeli ta reguła nie jest spełniona, transakcja jest błędna
         * i nie może zostać zapisana.
         */
        if (debit != credit) {
            throw new IllegalStateException("Unbalanced ledger transaction");
        }

        /**
         * Tworzymy nagłówek transakcji ledgerowej.
         *
         * Przykład:
         * - referenceType: PAYMENT
         * - referenceId: paymentId
         * - transactionType: MARKETPLACE_CAPTURE
         */
        LedgerTransaction transaction = transactionRepository.save(
                new LedgerTransaction(
                        referenceType,
                        referenceId,
                        transactionType
                )
        );

        /**
         * Zapisujemy wszystkie wpisy ledgerowe powiązane z transakcją.
         *
         * Każdy wpis dotyczy jednego konta i jednej strony księgowania:
         * DEBIT albo CREDIT.
         */
        lines.forEach(l -> entryRepository.save(new LedgerEntry(
                transaction.getTransactionId(),
                l.accountId(),
                l.direction(),
                l.amount(),
                l.currency()
        )));
    }

    /**
     * Pomocniczy rekord reprezentujący pojedynczą linię księgowania.
     *
     * Używamy go tylko wewnątrz LedgerService,
     * żeby wygodnie zbudować listę wpisów przed zapisem do bazy.
     *
     * Finalnie każda Line zostaje zapisana jako LedgerEntry.
     */
    private record Line(
            String accountId,
            LedgerDirection direction,
            long amount,
            String currency
    ) {
    }
}