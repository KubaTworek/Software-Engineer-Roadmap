package pl.jakubtworek.lld.parkinglot;

/**
 * Osobna zmienność: wycena nie decyduje o wyborze miejsca ani o zamknięciu biletu.
 * Kontrakt wymaga nie-null PricingContext i czystego obliczenia bez I/O,
 * modyfikacji parkingu i powtórnego wejścia do jego metod. Zwraca nie-null Money.
 * Kontekst już gwarantuje poprawny przedział; cenniki nie powielają walidacji
 * dat ani nie otrzymują całego biletu. Ten sam interfejs realizują strategia
 * bazowa i dekorator, który dodatkowo ogranicza wynik innej strategii.
 */
@FunctionalInterface
public interface FeePolicy {

    Money calculate(PricingContext context);
}
