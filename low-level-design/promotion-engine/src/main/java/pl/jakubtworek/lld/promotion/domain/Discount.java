package pl.jakubtworek.lld.promotion.domain;

/**
 * Strategia proponuje kwotę rabatu od aktualnej kwalifikowanej podstawy. Dla
 * zakresu all jest to całe saldo, dla category tylko saldo wybranych linii.
 * Nie zatwierdza jej:
 * silnik ograniczy ją saldem i wspólnym budżetem. Własne strategie mają być
 * bezstanowe i zwracać nieujemną Money, nigdy null. Nie otrzymują uchwytu
 * pozwalającego zmieniać koszyk lub publikować wynik wyceny.
 */
@FunctionalInterface
public interface Discount {

    Money calculate(Money remaining);
}
