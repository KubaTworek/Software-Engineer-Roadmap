# Zadania: monotonic deque

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/19-monotonic-deque.md)

Każde zadanie jest niezależne; metody pozostają TODO. Importy java.util.* zapewnia skrypt.

## DEQUE-01 — minimum każdego okna

**Poziom:** L1
**Wymagania:** [monotonic deque](../patterns/19-monotonic-deque.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Dla int[] i 1≤k≤n zwróć minimum każdego spójnego okna długości k. n w 1..100 000, bez mutacji. Docelowo O(n) czasu, O(k) pamięci poza wynikiem.

<details>
<summary>Wskazówka 1</summary>

Przód ma reprezentować najlepszy niewygasły indeks.

</details>

<details>
<summary>Wskazówka 2</summary>

Z tyłu usuń wartości zdominowane przez nową, mniejszą lub równą.

</details>

<details>
<summary>Wskazówka 3</summary>

Przed odczytem wyniku usuń z przodu indeksy poza oknem.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:DEQUE-01 -->
```java
static int[] slidingMinimum(int[] values, int k) {
    throw new UnsupportedOperationException("TODO DEQUE-01");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:DEQUE-01 -->
```java
static void verify() {
    assert Arrays.equals(slidingMinimum(new int[] {4,2,2,5,1}, 3), new int[] {2,2,1});
    assert Arrays.equals(slidingMinimum(new int[] {3,1,2}, 1), new int[] {3,1,2});
    assert Arrays.equals(slidingMinimum(new int[] {3,1,2}, 3), new int[] {1});
    assert Arrays.equals(slidingMinimum(new int[] {Integer.MIN_VALUE,0}, 2), new int[] {Integer.MIN_VALUE});
}
```

## DEQUE-02 — ograniczona różnica w fragmencie

**Poziom:** L2
**Wymagania:** [monotonic deque](../patterns/19-monotonic-deque.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Zwróć najdłuższy spójny fragment, w którym maksimum minus minimum nie przekracza nieujemnego limit typu long. Wartości int, n w 0..100 000, puste wejście daje 0. Bez mutacji. O(n) czasu i O(n) pamięci.

<details>
<summary>Wskazówka 1</summary>

Jak sprawdzić poprawność okna bez skanowania go od nowa?

</details>

<details>
<summary>Wskazówka 2</summary>

Utrzymuj minimum i maksimum w dwóch osobnych kolejkach indeksów.

</details>

<details>
<summary>Wskazówka 3</summary>

Gdy różnica jest za duża, skracaj okno i wygaszaj indeksy w obu kolejkach; odejmuj w long.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:DEQUE-02 -->
```java
static int longestLimitedDifference(int[] values, long limit) {
    throw new UnsupportedOperationException("TODO DEQUE-02");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:DEQUE-02 -->
```java
static void verify() {
    assert longestLimitedDifference(new int[] {8,2,4,7}, 4) == 2;
    assert longestLimitedDifference(new int[] {10,1,2,4,7,2}, 5) == 4;
    assert longestLimitedDifference(new int[] {2,2,2}, 0) == 3;
    assert longestLimitedDifference(new int[0], 0) == 0;
    assert longestLimitedDifference(new int[] {Integer.MIN_VALUE,Integer.MAX_VALUE}, 4294967295L) == 2;
    assert longestLimitedDifference(new int[] {Integer.MIN_VALUE,Integer.MAX_VALUE}, 4294967294L) == 1;
}
```

## DEQUE-03 — najkrótsza suma z ujemnymi liczbami

**Poziom:** L3
**Wymagania:** [monotonic deque](../patterns/19-monotonic-deque.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Zwróć minimalną długość niepustego spójnego fragmentu o sumie co najmniej dodatni target typu long. Wartości int mogą być ujemne; n w 0..100 000. Brak rozwiązania daje -1. Bez mutacji. O(n) czasu i O(n) pamięci.

<details>
<summary>Wskazówka 1</summary>

Suma fragmentu nadal jest różnicą dwóch prefiksów, choć zwykłe okno nie działa.

</details>

<details>
<summary>Wskazówka 2</summary>

Starszy prefiks o większej lub równej sumie jest gorszym początkiem od nowszego mniejszego prefiksu.

</details>

<details>
<summary>Wskazówka 3</summary>

Utrzymuj rosnące sumy prefiksów w deque; z przodu rozstrzygaj osiągnięty cel, z tyłu usuwaj zdominowane początki.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:DEQUE-03 -->
```java
static int shortestAtLeast(int[] values, long target) {
    throw new UnsupportedOperationException("TODO DEQUE-03");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:DEQUE-03 -->
```java
static void verify() {
    assert shortestAtLeast(new int[] {2,-1,2}, 3) == 3;
    assert shortestAtLeast(new int[] {1,-1,3}, 3) == 1;
    assert shortestAtLeast(new int[] {84,-37,32,40,95}, 167) == 3;
    assert shortestAtLeast(new int[] {-2,-1}, 1) == -1;
    assert shortestAtLeast(new int[0], 1) == -1;
    assert shortestAtLeast(new int[] {Integer.MAX_VALUE,Integer.MAX_VALUE}, 4294967294L) == 2;
}
```
