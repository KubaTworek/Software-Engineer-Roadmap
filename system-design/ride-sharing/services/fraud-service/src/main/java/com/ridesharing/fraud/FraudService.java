package com.ridesharing.fraud;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;

/**
 * Serwis oceny ryzyka fraudowego.
 *
 * W aplikacji ride-sharing ten komponent ocenia, czy dana operacja lub użytkownik
 * wygląda podejrzanie na podstawie prostych sygnałów behawioralnych i technicznych.
 *
 * To jest regułowy baseline fraud detection, nie model ML.
 * Każdy sygnał dodaje określoną liczbę punktów ryzyka, a końcowy score
 * jest mapowany na decyzję biznesową.
 */
@Service
public class FraudService {

    /**
     * Wykonuje ocenę ryzyka dla podmiotu z requestu.
     *
     * Flow:
     * 1. Startuje od score = 0.
     * 2. Analizuje sygnały ryzyka z requestu.
     * 3. Dodaje punkty i powody do listy reasons.
     * 4. Wzmacnia score, jeśli użyto promocji przy już podwyższonym ryzyku.
     * 5. Mapuje końcowy score na RiskDecision.
     * 6. Zwraca odpowiedź z decyzją, powodami i timestampem.
     *
     * subjectId i subjectType pozwalają oceniać różne podmioty:
     * pasażera, kierowcę, urządzenie, przejazd albo płatność.
     */
    public RiskAssessmentResponse assess(RiskAssessmentRequest request) {
        /*
         * Łączny wynik ryzyka.
         * Im wyższy score, tym bardziej restrykcyjna decyzja.
         */
        int score = 0;

        /*
         * Lista powodów, które wpłynęły na ocenę.
         * To ważne dla supportu, audytu i debugowania decyzji fraudowych.
         */
        var reasons = new ArrayList<String>();

        /*
         * Duża liczba anulowań w ostatnich 24h może oznaczać:
         * - nadużywanie systemu,
         * - testowanie promocji,
         * - spamowanie kierowców,
         * - problematyczne konto.
         */
        if (request.cancellationCountLast24h() >= 5) {
            score += 25;
            reasons.add("high_cancellation_rate");
        }

        /*
         * Kilka nieudanych płatności w ostatnich 7 dniach zwiększa ryzyko.
         *
         * Może oznaczać nieważną kartę, próby płatności cudzymi metodami,
         * fraud płatniczy albo użytkownika, który często kończy bez skutecznego capture.
         */
        if (request.paymentFailuresLast7d() >= 2) {
            score += 30;
            reasons.add("payment_failures");
        }

        /*
         * Wiele kont na jednym urządzeniu jest silnym sygnałem nadużyć.
         *
         * Typowe przypadki:
         * - omijanie blokad,
         * - nadużywanie promocji dla nowych kont,
         * - farmienie kont.
         */
        if (request.accountsOnDevice() >= 3) {
            score += 25;
            reasons.add("many_accounts_on_device");
        }

        /*
         * Duży skok GPS w krótkim czasie może oznaczać spoofing lokalizacji.
         *
         * W ride-sharingu jest to szczególnie ważne dla kierowców,
         * bo fałszywa lokalizacja może wpływać na matching, ETA i rozliczenia.
         */
        if (request.maxGpsJumpKmLast10m() > 5) {
            score += 30;
            reasons.add("gps_spoofing_suspected");
        }

        /*
         * Promocja sama w sobie nie jest problemem.
         * Ale jeśli konto ma już podwyższone ryzyko, użycie promocji wzmacnia score.
         *
         * To chroni przed promo abuse, np. tworzeniem wielu kont dla zniżek.
         */
        if (request.promoApplied() && score > 30) {
            score += 15;
            reasons.add("promo_risk_amplifier");
        }

        /*
         * Mapowanie score na decyzję.
         *
         * Progi:
         * - 80+: tymczasowe zawieszenie,
         * - 60–79: wymagana dodatkowa weryfikacja,
         * - 40–59: ręczny review,
         * - promo + 30–39: blokada promocji,
         * - poniżej: allow.
         *
         * Kolejność warunków ma znaczenie:
         * wyższe ryzyko ma pierwszeństwo przed łagodniejszymi decyzjami.
         */
        RiskDecision decision = score >= 80
                ? RiskDecision.TEMPORARILY_SUSPEND
                : score >= 60
                ? RiskDecision.REQUIRE_VERIFICATION
                : score >= 40
                ? RiskDecision.REVIEW
                : request.promoApplied() && score >= 30
                ? RiskDecision.BLOCK_PROMO
                : RiskDecision.ALLOW;

        /*
         * Score jest ograniczony do 100, żeby response mieścił się w czytelnej skali 0–100.
         */
        return new RiskAssessmentResponse(
                request.subjectId(),
                request.subjectType(),
                Math.min(score, 100),
                decision,
                reasons,
                Instant.now()
        );
    }
}