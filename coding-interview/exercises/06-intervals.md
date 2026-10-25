# Zadania: przedziały

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/06-intervals.md)

## INT-01 — wstaw przedział i scal kolizje

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/06-intervals.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Wejście dzieli się na część przed, kolidującą i po nowym zakresie.

</details>

<details>
<summary>Wskazówka 2</summary>

Scal tylko fragment łączący się z dodanym przedziałem.

</details>

<details>
<summary>Wskazówka 3</summary>

Domknięte końce łączą się przy równości; wynik ma własne wiersze.

</details>

**Treść i kontrakt.** Wejście zawiera domknięte przedziały `[start,end]`,
`start <= end`, posortowane po początku i parami rozłączne (także bez wspólnych
końców). Dodaj nowy przedział i zwróć uporządkowany, rozłączny wynik o tym samym
pokryciu. Wspólny punkt wymaga scalenia. n w 0..100 000. Nie zmieniaj wejścia
ani nowego przedziału; wynik nie może współdzielić ich wierszy.
`[[1,3],[6,9]]` i `[2,5]` → `[[1,5],[6,9]]`. Docelowo O(n) czasu, O(n) wyniku.

Kontekst: nierozwiązany szkielet.

<!-- exercise:INT-01 -->
```java
static int[][] insertInterval(int[][] intervals, int[] added) {
    throw new UnsupportedOperationException("TODO INT-01");
}
```

Kontekst: wykonywalne testy punktów styku i niezależności danych.

<!-- tests:INT-01 -->
```java
static void verify() {
    int[][] input = {{1, 3}, {6, 9}};
    int[] added = {2, 5};
    int[][] result = insertInterval(input, added);
    assert Arrays.deepEquals(result, new int[][] {{1, 5}, {6, 9}});
    result[0][0] = -10;
    result[1][1] = 99;
    assert Arrays.deepEquals(input, new int[][] {{1, 3}, {6, 9}});
    assert Arrays.equals(added, new int[] {2, 5});
    assert Arrays.deepEquals(insertInterval(new int[][] {{1, 2}, {4, 5}}, new int[] {2, 4}),
            new int[][] {{1, 5}});
    assert Arrays.deepEquals(insertInterval(new int[][] {{1, 8}}, new int[] {2, 3}),
            new int[][] {{1, 8}});
    assert Arrays.deepEquals(insertInterval(new int[0][], new int[] {7, 7}), new int[][] {{7, 7}});
    assert Arrays.deepEquals(insertInterval(new int[][] {{3, 4}}, new int[] {0, 1}),
            new int[][] {{0, 1}, {3, 4}});
    assert Arrays.deepEquals(insertInterval(new int[][] {{1, 2}}, new int[] {5, 6}),
            new int[][] {{1, 2}, {5, 6}});
}
```

## INT-02 — wspólne pokrycie dwóch list

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/06-intervals.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Wystarczy porównywać bieżące przedziały obu list.

</details>

<details>
<summary>Wskazówka 2</summary>

Przecięcie ma większy początek i mniejszy koniec.

</details>

<details>
<summary>Wskazówka 3</summary>

Przesuń listę z wcześniejszym końcem; wspólny punkt jest poprawnym wynikiem.

</details>

**Treść i kontrakt.** Każda z dwóch list zawiera posortowane po początku,
parami rozłączne przedziały domknięte; wspólnych końców wewnątrz jednej listy
również nie ma. Zwróć posortowaną listę przecięć między listami. Pojedynczy wspólny
punkt jest przedziałem `[x,x]`. Długości 0..100 000. Nie zmieniaj wejść ani nie
współdziel wierszy wyniku z wejściem. Docelowo O(n + m) czasu poza zapisem wyniku.

Kontekst: nierozwiązany szkielet.

<!-- exercise:INT-02 -->
```java
static int[][] intersectIntervals(int[][] first, int[][] second) {
    throw new UnsupportedOperationException("TODO INT-02");
}
```

Kontekst: wykonywalne testy.

<!-- tests:INT-02 -->
```java
static void verify() {
    int[][] first = {{0, 2}, {5, 10}};
    int[][] second = {{1, 5}, {8, 12}};
    int[][] result = intersectIntervals(first, second);
    assert Arrays.deepEquals(result, new int[][] {{1, 2}, {5, 5}, {8, 10}});
    result[0][0] = -99;
    assert Arrays.deepEquals(first, new int[][] {{0, 2}, {5, 10}});
    assert Arrays.deepEquals(second, new int[][] {{1, 5}, {8, 12}});
    assert intersectIntervals(new int[0][], second).length == 0;
    assert intersectIntervals(first, new int[0][]).length == 0;
    assert intersectIntervals(new int[][] {{0, 1}}, new int[][] {{2, 3}}).length == 0;
    assert Arrays.deepEquals(intersectIntervals(new int[][] {{1, 2}}, new int[][] {{2, 4}}),
            new int[][] {{2, 2}});
    assert Arrays.deepEquals(intersectIntervals(new int[][] {{1, 9}}, new int[][] {{2, 3}}),
            new int[][] {{2, 3}});
}
```

## INT-03 — minimalna liczba sal

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/06-intervals.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Liczba sal zależy od jednoczesnej zajętości.

</details>

<details>
<summary>Wskazówka 2</summary>

Śledź zdarzenia początku i końca albo końce zajętych sal.

</details>

<details>
<summary>Wskazówka 3</summary>

Przy równej chwili zwolnienie sali może poprzedzić rozpoczęcie.

</details>

**Treść i kontrakt.** Wszystkie spotkania muszą się odbyć. Są półotwartymi
przedziałami int `[start,end)`, `start < end`. Zwróć minimalną liczbę sal, aby
spotkania w jednej sali nie zachodziły na siebie. Koniec o 10 pozwala zacząć
następne o 10. n w 0..100 000, bez mutacji wejścia. `[[0,30],[5,10],[15,20]]`
→ 2. Docelowo O(n log n) czasu i O(n) pamięci.

Kontekst: nierozwiązany szkielet.

<!-- exercise:INT-03 -->
```java
static int minRooms(int[][] meetings) {
    throw new UnsupportedOperationException("TODO INT-03");
}
```

Kontekst: wykonywalne testy odróżniają liczbę sal od liczby scalonych przedziałów.

<!-- tests:INT-03 -->
```java
static void verify() {
    int[][] input = {{15, 20}, {0, 30}, {5, 10}};
    assert minRooms(input) == 2;
    assert Arrays.deepEquals(input, new int[][] {{15, 20}, {0, 30}, {5, 10}});
    assert minRooms(new int[][] {{1, 2}, {2, 3}}) == 1;
    assert minRooms(new int[][] {{1, 4}, {1, 4}, {1, 4}}) == 3;
    assert minRooms(new int[][] {{0, 10}, {1, 9}, {2, 8}}) == 3;
    assert minRooms(new int[][] {{-5, -1}, {-1, 2}}) == 1;
    assert minRooms(new int[0][]) == 0;
}
```
