package com.example.paymentsystem.fx;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Serwis odpowiedzialny za prostą konwersję walut.
 *
 * W kontekście Payment Systemu jest używany wtedy, gdy waluta płatności
 * różni się od waluty rozliczeniowej merchanta.
 *
 * Przykład:
 * - klient płaci w EUR,
 * - merchant rozlicza się w PLN,
 * - system musi zapisać zarówno kwotę oryginalną, jak i kwotę settlementu.
 *
 * To jest uproszczona implementacja edukacyjna.
 * W produkcji kursy powinny pochodzić z zewnętrznego źródła, np. banku,
 * PSP albo dedykowanego FX provider API.
 */
@Service
public class FxService {

    /**
     * Lokalna tabela kursów względem PLN.
     *
     * Interpretacja:
     * - 1 PLN = 1.0000 PLN
     * - 1 EUR = 4.3000 PLN
     * - 1 USD = 4.0000 PLN
     * - 1 GBP = 5.0500 PLN
     *
     * Dzięki temu możemy przeliczać waluty przez PLN jako walutę bazową.
     *
     * Przykład:
     * EUR -> USD:
     * - EUR do PLN: 4.3000
     * - USD do PLN: 4.0000
     * - kurs EUR/USD = 4.3000 / 4.0000 = 1.075
     */
    private static final Map<String, BigDecimal> PLN_RATES = Map.of(
            "PLN", new BigDecimal("1.0000"),
            "EUR", new BigDecimal("4.3000"),
            "USD", new BigDecimal("4.0000"),
            "GBP", new BigDecimal("5.0500")
    );

    /**
     * Konwertuje kwotę z jednej waluty na drugą.
     *
     * Kwota jest typu long, bo w systemach płatniczych nie używamy float/double
     * do pieniędzy. Przechowujemy wartości w najmniejszej jednostce waluty.
     *
     * Przykład:
     * - 10000 PLN oznacza 100,00 PLN,
     * - 2500 EUR oznacza 25,00 EUR.
     *
     * @param amount kwota w najmniejszej jednostce waluty źródłowej
     * @param sourceCurrency waluta płatności, np. EUR
     * @param targetCurrency waluta settlementu merchanta, np. PLN
     * @return obiekt FxQuote z kwotą źródłową, docelową i użytym kursem
     */
    public FxQuote convert(long amount, String sourceCurrency, String targetCurrency) {

        /**
         * Jeżeli waluta płatności i waluta settlementu są takie same,
         * nie wykonujemy żadnego przeliczenia.
         *
         * To najprostszy i najczęstszy przypadek, np.:
         * - klient płaci w PLN,
         * - merchant rozlicza się w PLN.
         */
        if (sourceCurrency.equals(targetCurrency)) {
            return new FxQuote(
                    amount,
                    sourceCurrency,
                    amount,
                    targetCurrency,
                    "1.0000"
            );
        }

        /**
         * Pobieramy kurs waluty źródłowej względem PLN.
         *
         * Przykład:
         * sourceCurrency = EUR
         * sourceToPln = 4.3000
         *
         * Jeżeli waluty nie ma w mapie, używamy BigDecimal.ONE.
         * W produkcji lepiej byłoby rzucić wyjątek, bo ciche użycie kursu 1.0
         * może prowadzić do błędnego settlementu.
         */
        BigDecimal sourceToPln = PLN_RATES.getOrDefault(sourceCurrency, BigDecimal.ONE);

        /**
         * Pobieramy kurs waluty docelowej względem PLN.
         *
         * Przykład:
         * targetCurrency = USD
         * targetToPln = 4.0000
         */
        BigDecimal targetToPln = PLN_RATES.getOrDefault(targetCurrency, BigDecimal.ONE);

        /**
         * Wyliczamy kurs source -> target przez PLN jako walutę bazową.
         *
         * Formula:
         * sourceToTarget = sourceToPln / targetToPln
         *
         * Przykład:
         * EUR -> USD:
         * 4.3000 / 4.0000 = 1.075000
         *
         * Używamy BigDecimal, żeby uniknąć błędów typowych dla double/float.
         */
        BigDecimal rate = sourceToPln.divide(
                targetToPln,
                6,
                RoundingMode.HALF_UP
        );

        /**
         * Przeliczamy kwotę na walutę docelową.
         *
         * Wynik zaokrąglamy do liczby całkowitej, bo nadal operujemy
         * na najmniejszej jednostce waluty, np. groszach/centach.
         *
         * Przykład:
         * 10000 EUR-centów * 4.3000 = 43000 PLN-groszy
         */
        long targetAmount = BigDecimal.valueOf(amount)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        /**
         * Zwracamy pełną informację o przeliczeniu.
         *
         * Dzięki temu Payment może zapisać:
         * - kwotę oryginalną,
         * - walutę oryginalną,
         * - kwotę settlementu,
         * - walutę settlementu,
         * - kurs użyty do przeliczenia.
         *
         * To jest ważne dla audytu, raportowania i późniejszego reconciliation.
         */
        return new FxQuote(
                amount,
                sourceCurrency,
                targetAmount,
                targetCurrency,
                rate.toPlainString()
        );
    }
}