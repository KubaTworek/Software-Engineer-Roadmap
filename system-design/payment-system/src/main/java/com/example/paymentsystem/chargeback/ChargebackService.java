package com.example.paymentsystem.chargeback;

import com.example.paymentsystem.ledger.LedgerService;
import com.example.paymentsystem.payment.Payment;
import com.example.paymentsystem.payment.PaymentException;
import com.example.paymentsystem.payment.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serwis obsługujący chargebacki, czyli spory płatnicze zgłoszone poza naszym systemem,
 * np. przez klienta w banku albo u operatora płatności.
 *
 * To nie jest zwykły refund.
 * Refund inicjujemy sami, a chargeback przychodzi z zewnątrz i może odebrać środki merchantowi.
 *
 * Ta klasa odpowiada za:
 * - otwarcie chargebacku,
 * - rozstrzygnięcie chargebacku jako przegrany,
 * - rozstrzygnięcie chargebacku jako wygrany,
 * - zmianę statusu powiązanej płatności,
 * - księgowanie straty w ledgerze, gdy chargeback jest przegrany.
 */
@Service
public class ChargebackService {

    /**
     * Repozytorium chargebacków.
     * Służy do zapisu nowego sporu oraz pobierania istniejącego chargebacku
     * przy jego rozstrzyganiu.
     */
    private final ChargebackRepository chargebackRepository;

    /**
     * Repozytorium płatności.
     *
     * Używamy findByIdForUpdate, czyli pobrania płatności z blokadą.
     * Dzięki temu w tym samym czasie inny proces nie powinien równolegle
     * zmienić tej samej płatności, np. przez refund, payout albo inny chargeback.
     */
    private final PaymentRepository paymentRepository;

    /**
     * Ledger odpowiada za finansowe skutki operacji.
     *
     * Sam status chargebacku nie wystarcza.
     * Jeżeli chargeback jest przegrany, trzeba zaksięgować realne obciążenie merchanta.
     */
    private final LedgerService ledgerService;

    public ChargebackService(
            ChargebackRepository chargebackRepository,
            PaymentRepository paymentRepository,
            LedgerService ledgerService
    ) {
        this.chargebackRepository = chargebackRepository;
        this.paymentRepository = paymentRepository;
        this.ledgerService = ledgerService;
    }

    /**
     * Otwiera chargeback dla konkretnej płatności.
     *
     * Flow:
     * 1. Pobieramy płatność z blokadą.
     * 2. Sprawdzamy w encji Payment, czy można otworzyć chargeback.
     * 3. Zmieniamy status płatności na CHARGEBACK_OPENED.
     * 4. Zapisujemy nowy rekord Chargeback.
     *
     * Na tym etapie nie księgujemy jeszcze straty.
     * Otwarcie chargebacku oznacza tylko, że istnieje spór.
     */
    @Transactional
    public Chargeback open(UUID paymentId, OpenChargebackRequest request) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found"));

        payment.openChargeback();

        return chargebackRepository.save(new Chargeback(
                paymentId,
                request.amount(),
                payment.getCurrency(),
                request.reason()
        ));
    }

    /**
     * Oznacza chargeback jako przegrany.
     *
     * To operacja, która ma realny wpływ na pieniądze.
     *
     * Flow:
     * 1. Pobieramy chargeback.
     * 2. Pobieramy powiązaną płatność z blokadą.
     * 3. Zmieniamy status płatności na CHARGEBACK_LOST.
     * 4. Zmieniamy status chargebacku na LOST.
     * 5. Księgujemy stratę w ledgerze.
     *
     * Ledger zapisuje, że merchant traci środki.
     * Dzięki temu saldo merchanta jest zgodne z rzeczywistym rozliczeniem.
     */
    @Transactional
    public Chargeback lose(UUID chargebackId) {
        Chargeback chargeback = chargebackRepository.findById(chargebackId)
                .orElseThrow();

        Payment payment = paymentRepository.findByIdForUpdate(chargeback.getPaymentId())
                .orElseThrow();

        payment.loseChargeback();
        chargeback.lose();

        ledgerService.recordChargebackLoss(
                chargeback.getChargebackId(),
                payment.getMerchantId(),
                chargeback.getAmount(),
                chargeback.getCurrency()
        );

        return chargeback;
    }

    /**
     * Oznacza chargeback jako wygrany.
     *
     * Flow:
     * 1. Pobieramy chargeback.
     * 2. Pobieramy powiązaną płatność z blokadą.
     * 3. Zmieniamy status płatności na CHARGEBACK_WON.
     * 4. Zmieniamy status chargebacku na WON.
     *
     * W tej wersji nie księgujemy dodatkowych wpisów w ledgerze.
     * Zakładamy, że skoro chargeback został wygrany, merchant nie traci środków.
     */
    @Transactional
    public Chargeback win(UUID chargebackId) {
        Chargeback chargeback = chargebackRepository.findById(chargebackId)
                .orElseThrow();

        Payment payment = paymentRepository.findByIdForUpdate(chargeback.getPaymentId())
                .orElseThrow();

        payment.winChargeback();
        chargeback.win();

        return chargeback;
    }
}