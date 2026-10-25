# Zadania: binary search

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/05-binary-search.md)

## BIN-01 — cały zakres wystąpień

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/05-binary-search.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Potrzebujesz dwóch różnych granic porównania.

</details>

<details>
<summary>Wskazówka 2</summary>

Szukaj pierwszego elementu co najmniej równego i pierwszego większego.

</details>

<details>
<summary>Wskazówka 3</summary>

Nie używaj target+1 w int, bo MAX_VALUE jest poprawnym celem.

</details>

**Treść i kontrakt.** Dla niemalejącej tablicy int zwróć dwuelementową tablicę
`[first,afterLast]` wyznaczającą półotwarty zakres wszystkich wystąpień target.
Jeśli target nie występuje, obie granice mają wskazywać jego miejsce wstawienia.
Długość 0..100 000, bez mutacji. `[1,2,2,4]`, target 2 → `[1,3]`, target 3 → `[3,3]`.
Docelowo O(log(n + 1)) czasu i O(1) pamięci. Uwzględnij MAX_VALUE bez zawijania celu.

Kontekst: nierozwiązany szkielet.

<!-- exercise:BIN-01 -->
```java
static int[] equalRange(int[] sorted, int target) {
    throw new UnsupportedOperationException("TODO BIN-01");
}
```

Kontekst: wykonywalne testy.

<!-- tests:BIN-01 -->
```java
static void verify() {
    assert Arrays.equals(equalRange(new int[] {1, 2, 2, 4}, 2), new int[] {1, 3});
    assert Arrays.equals(equalRange(new int[] {1, 2, 2, 4}, 3), new int[] {3, 3});
    assert Arrays.equals(equalRange(new int[] {2, 2}, 2), new int[] {0, 2});
    assert Arrays.equals(equalRange(new int[] {2, 3}, 1), new int[] {0, 0});
    assert Arrays.equals(equalRange(new int[] {2, 3}, 4), new int[] {2, 2});
    assert Arrays.equals(equalRange(new int[0], 5), new int[] {0, 0});
    assert Arrays.equals(equalRange(new int[] {Integer.MAX_VALUE}, Integer.MAX_VALUE),
            new int[] {0, 1});
}
```

## BIN-02 — całkowity pierwiastek bez funkcji pierwiastka

**Poziom:** L1
**Wymagania:** [Karta techniki](../patterns/05-binary-search.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Warunek r²≤value ma uporządkowaną dziedzinę.

</details>

<details>
<summary>Wskazówka 2</summary>

Szukaj ostatniego poprawnego kandydata.

</details>

<details>
<summary>Wskazówka 3</summary>

Mnożenie wykonaj w long i sprawdź postęp przy przedziale długości jeden.

</details>

**Treść i kontrakt.** Dla `0 <= value <= Integer.MAX_VALUE` zwróć największą
nieujemną liczbę int r, dla której `r² <= value`. Nie używaj sqrt, pow ani obliczeń
zmiennoprzecinkowych. 8 → 2, 9 → 3. Docelowo O(log(value + 2)) czasu i O(1) pamięci.

Kontekst: nierozwiązany szkielet.

<!-- exercise:BIN-02 -->
```java
static int integerSqrt(int value) {
    throw new UnsupportedOperationException("TODO BIN-02");
}
```

Kontekst: wykonywalne testy wartości oraz definiującej je własności.

<!-- tests:BIN-02 -->
```java
static void verify() {
    assert integerSqrt(0) == 0;
    assert integerSqrt(1) == 1;
    assert integerSqrt(8) == 2;
    assert integerSqrt(9) == 3;
    assert integerSqrt(Integer.MAX_VALUE) == 46340;
    for (int value : new int[] {2, 3, 15, 16, 17, 1000, 2147395600}) {
        int root = integerSqrt(value);
        assert root >= 0;
        assert (long) root * root <= value;
        assert ((long) root + 1) * ((long) root + 1) > value;
    }
}
```

## BIN-03 — minimalna pojemność transportu

**Poziom:** L3
**Wymagania:** [Karta techniki](../patterns/05-binary-search.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Większa pojemność nie zwiększa wymaganej liczby dni.

</details>

<details>
<summary>Wskazówka 2</summary>

Dla ustalonej pojemności policz dni liniowym przeglądem.

</details>

<details>
<summary>Wskazówka 3</summary>

Szukaj najmniejszej wykonalnej wartości między maksimum i sumą wag w long.

</details>

**Treść i kontrakt.** Paczki o dodatnich wagach int trzeba przewieźć w kolejności
wejścia w co najwyżej days dni. Każdego dnia zabierasz spójny kolejny fragment,
bez dzielenia paczki; dni mogą zostać niewykorzystane. Zwróć minimalną całkowitą
pojemność pojazdu jako long. n w 0..100 000, days w 1..100 000; puste wejście → 0.
Nie zmieniaj wag. `[1,2,3,4,5]`, days = 3 → 6. Docelowo O(n·log(S + 1)) czasu
dla niepustego wejścia, gdzie S jest sumą wag, i O(1) pamięci. To ćwiczenie po
odpowiedzi: samą monotoniczność musisz uzasadnić przed implementacją.

Kontekst: nierozwiązany szkielet.

<!-- exercise:BIN-03 -->
```java
static long minimumCapacity(int[] weights, int days) {
    throw new UnsupportedOperationException("TODO BIN-03");
}
```

Kontekst: wykonywalne testy.

<!-- tests:BIN-03 -->
```java
static void verify() {
    int[] input = {1, 2, 3, 4, 5};
    assert minimumCapacity(input, 3) == 6;
    assert Arrays.equals(input, new int[] {1, 2, 3, 4, 5});
    assert minimumCapacity(new int[] {3, 2, 2, 4, 1, 4}, 3) == 6;
    assert minimumCapacity(new int[] {1, 2, 3}, 1) == 6;
    assert minimumCapacity(new int[] {1, 2, 3}, 9) == 3;
    assert minimumCapacity(new int[0], 1) == 0;
    assert minimumCapacity(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 1) == 4294967294L;
    assert minimumCapacity(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 2) == Integer.MAX_VALUE;
}
```
