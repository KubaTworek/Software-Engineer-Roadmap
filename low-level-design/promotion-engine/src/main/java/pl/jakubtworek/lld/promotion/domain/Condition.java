package pl.jakubtworek.lld.promotion.domain;

/**
 * Zmienny warunek kwalifikacji. Implementacja ma być deterministyczna, bez
 * efektów ubocznych i zależności od niejawnego czasu. Wynik false jest zwykłą
 * odmową kwalifikacji; wyjątek oznacza błąd, którego silnik nie ukrywa.
 */
@FunctionalInterface
public interface Condition {

    boolean matches(PricingContext context);

    /**
     * Własne lambdy zachowują kontrakt boolean, a ich wynik odczytujemy tylko raz.
     */
    default ConditionEvaluation evaluate(PricingContext context) {
        return ConditionEvaluation.leaf("CUSTOM", matches(context));
    }

    /**
     * Opis pominięcia nie może uruchamiać predykatu ani czytać faktów przez niego.
     */
    default ConditionEvaluation skipped() {
        return new ConditionEvaluation("CUSTOM", ConditionEvaluation.Outcome.SKIPPED, java.util.List.of());
    }
}
