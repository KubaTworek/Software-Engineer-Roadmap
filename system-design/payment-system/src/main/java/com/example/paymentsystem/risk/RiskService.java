package com.example.paymentsystem.risk;

import com.example.paymentsystem.payment.CreatePaymentRequest;
import com.example.paymentsystem.payment.RiskDecision;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Serwis odpowiedzialny za prostą ocenę ryzyka płatności.
 *
 * Risk scoring jest wykonywany przed utworzeniem płatności u PSP.
 * Dzięki temu system może zablokować podejrzaną transakcję zanim:
 * - wyślemy ją do providera płatności,
 * - klient zostanie przekierowany do checkoutu,
 * - powstanie realne ryzyko fraudu lub chargebacku.
 *
 * W tej wersji scoring jest regułowy.
 * W produkcji taki moduł mógłby korzystać z:
 * - historii klienta,
 * - velocity checks,
 * - device fingerprintingu,
 * - list sankcyjnych,
 * - modeli ML,
 * - zewnętrznych systemów fraud detection.
 */
@Service
public class RiskService {

    /**
     * Próg, od którego płatność jest blokowana.
     *
     * Jeżeli score >= highRiskThreshold,
     * decyzja końcowa to BLOCK.
     *
     * Wartość pochodzi z konfiguracji:
     * payment-system.risk.high-risk-threshold
     */
    private final int highRiskThreshold;

    /**
     * Próg, od którego płatność wymaga ręcznej weryfikacji.
     *
     * Jeżeli score >= reviewThreshold,
     * ale score < highRiskThreshold,
     * decyzja końcowa to REVIEW.
     *
     * Wartość pochodzi z konfiguracji:
     * payment-system.risk.review-threshold
     */
    private final int reviewThreshold;

    public RiskService(
            @Value("${payment-system.risk.high-risk-threshold:80}") int highRiskThreshold,
            @Value("${payment-system.risk.review-threshold:50}") int reviewThreshold
    ) {
        this.highRiskThreshold = highRiskThreshold;
        this.reviewThreshold = reviewThreshold;
    }

    /**
     * Ocenia ryzyko płatności na podstawie danych z requestu.
     *
     * Wynikiem jest RiskAssessment zawierający:
     * - score,
     * - decyzję: ALLOW, REVIEW albo BLOCK,
     * - powód / typ scoringu.
     *
     * Decyzja risk engine jest później używana w PaymentService.
     * Dla BLOCK płatność nie powinna zostać utworzona.
     */
    public RiskAssessment assess(CreatePaymentRequest request) {
        int score = 0;

        /**
         * Podbijamy score dla większych płatności.
         *
         * Kwoty trzymamy w najmniejszej jednostce waluty.
         * Przykład:
         * 100_000 = 1000,00 PLN/EUR/USD przy walucie z dwoma miejscami po przecinku.
         *
         * Duża kwota oznacza większe potencjalne ryzyko fraudu
         * i większą stratę przy chargebacku.
         */
        if (request.amount() > 100_000) {
            score += 35;
        }

        /**
         * Bardzo duża kwota dokłada kolejne punkty ryzyka.
         *
         * Ta reguła kumuluje się z poprzednią.
         * Czyli płatność > 500_000 dostaje:
         * - 35 punktów za przekroczenie 100_000,
         * - dodatkowe 35 punktów za przekroczenie 500_000.
         */
        if (request.amount() > 500_000) {
            score += 35;
        }

        /**
         * Podbijamy score, gdy kraj klienta i kraj IP są różne.
         *
         * Przykład:
         * customerCountry = PL
         * ipCountry = RU
         *
         * Taka rozbieżność nie musi oznaczać fraudu,
         * ale jest silnym sygnałem ryzyka.
         */
        if (request.customerCountry() != null
                && request.ipCountry() != null
                && !request.customerCountry().equals(request.ipCountry())) {
            score += 30;
        }

        /**
         * Lekki risk bump dla USD.
         *
         * W prawdziwym systemie reguły walutowe zależałyby od biznesu,
         * kraju merchanta, regionu klienta, typu produktu i danych historycznych.
         *
         * Tutaj chodzi o pokazanie, że risk engine może brać pod uwagę
         * również walutę transakcji.
         */
        if ("USD".equals(request.currency())) {
            score += 10;
        }

        /**
         * Zamieniamy score na decyzję biznesową.
         *
         * BLOCK:
         * - płatność jest zbyt ryzykowna,
         * - PaymentService powinien ją odrzucić.
         *
         * REVIEW:
         * - płatność nie jest automatycznie blokowana,
         * - ale powinna trafić do ręcznej weryfikacji lub dodatkowego procesu.
         *
         * ALLOW:
         * - płatność przechodzi normalnie do routingu PSP.
         */
        RiskDecision decision = score >= highRiskThreshold
                ? RiskDecision.BLOCK
                : score >= reviewThreshold
                ? RiskDecision.REVIEW
                : RiskDecision.ALLOW;

        /**
         * Zwracamy kompletną ocenę ryzyka.
         *
         * PaymentService zapisuje score i decyzję na płatności,
         * dzięki czemu można później raportować i audytować,
         * dlaczego dana płatność została przepuszczona albo zablokowana.
         */
        return new RiskAssessment(
                score,
                decision,
                "rule_based_score"
        );
    }
}