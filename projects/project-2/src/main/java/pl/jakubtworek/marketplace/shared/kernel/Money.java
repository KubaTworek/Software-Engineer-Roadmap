package pl.jakubtworek.marketplace.shared.kernel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Value object reprezentujący wartość pieniężną.
 *
 * Money łączy kwotę oraz walutę w jeden spójny typ domenowy.
 * Dzięki temu nie przekazujemy po systemie osobno BigDecimal i String/Currency,
 * co zmniejsza ryzyko pomyłek, np. dodania kwoty PLN do kwoty EUR.
 *
 * Jako value object Money powinno być:
 * - niemutowalne,
 * - porównywalne po wartości,
 * - pozbawione tożsamości technicznej,
 * - odpowiedzialne za pilnowanie własnych invariantów.
 *
 * Record dobrze pasuje do tego przypadku, bo automatycznie dostarcza:
 * - finalne pola,
 * - equals(...),
 * - hashCode(),
 * - toString().
 */
public record Money(BigDecimal amount, Currency currency) {

    /**
     * Konstruktor kanoniczny recorda.
     *
     * W tym miejscu centralizujemy walidację i normalizację kwoty.
     *
     * Reguły:
     * - amount nie może być null,
     * - currency nie może być null,
     * - kwota jest normalizowana do dwóch miejsc po przecinku,
     * - kwota nie może być ujemna.
     *
     * Uwaga:
     * setScale(2, RoundingMode.HALF_UP) oznacza, że wartość zostanie zaokrąglona,
     * np. 10.999 stanie się 11.00. To jest wygodne, ale w niektórych systemach
     * finansowych lepiej jawnie odrzucać kwoty z nadmiarową precyzją zamiast je zaokrąglać.
     */
    public Money {
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(currency, "currency cannot be null");

        amount = amount.setScale(2, RoundingMode.HALF_UP);

        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
    }

    /**
     * Tworzy Money na podstawie tekstowej kwoty i tekstowego kodu waluty.
     *
     * Przykład:
     * Money.of("199.99", "PLN")
     *
     * Ta metoda jest wygodna przy mapowaniu danych z API HTTP, gdzie kwota i waluta
     * często przychodzą jako tekst.
     *
     * BigDecimal(String) jest celowo lepszy niż BigDecimal(double), bo unika błędów
     * precyzji typowych dla liczb zmiennoprzecinkowych.
     */
    public static Money of(String amount, String currency) {
        return new Money(
                new BigDecimal(amount),
                Currency.getInstance(currency)
        );
    }

    /**
     * Tworzy Money na podstawie BigDecimal i Currency.
     *
     * Ta metoda jest wygodna przy odtwarzaniu wartości z bazy danych, gdzie kwota
     * zwykle jest odczytywana jako BigDecimal, a waluta jako kod ISO.
     */
    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    /**
     * Dodaje dwie wartości pieniężne.
     *
     * Operacja jest dozwolona tylko wtedy, gdy obie wartości mają tę samą walutę.
     * Nie można bezpośrednio dodać np. 100 PLN i 20 EUR, bo wymagałoby to kursu wymiany,
     * a to jest osobna logika domenowa.
     *
     * Zwracany jest nowy obiekt Money. Obecny obiekt nie jest modyfikowany.
     */
    public Money add(Money other) {
        assertSameCurrency(other);

        return new Money(
                amount.add(other.amount),
                currency
        );
    }

    /**
     * Mnoży kwotę przez liczbę całkowitą.
     *
     * W tym projekcie metoda jest używana np. do obliczania wartości linii zamówienia:
     * unitPrice * quantity.
     *
     * Mnożnik nie może być ujemny.
     * Mnożnik równy 0 jest technicznie dozwolony, ale w domenie zamówień ilość produktu
     * powinna być walidowana osobno jako większa od zera.
     *
     * Zwracany jest nowy obiekt Money.
     */
    public Money multiply(int multiplier) {
        if (multiplier < 0) {
            throw new IllegalArgumentException("multiplier cannot be negative");
        }

        return new Money(
                amount.multiply(BigDecimal.valueOf(multiplier)),
                currency
        );
    }

    /**
     * Sprawdza, czy dwie wartości pieniężne mają tę samą walutę.
     *
     * To pomocnicza metoda chroniąca operacje arytmetyczne przed mieszaniem walut.
     */
    private void assertSameCurrency(Money other) {
        Objects.requireNonNull(other, "other money cannot be null");

        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("currency mismatch");
        }
    }
}