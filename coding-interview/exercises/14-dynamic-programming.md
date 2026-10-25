# Zadania: dynamic programming

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/14-dynamic-programming.md)

Przed implementacją zapisz znaczenie stanu, bazę, przejście i kolejność obliczeń.
W samych szkieletach nie ma przejść ani podpowiedzi w postaci gotowych stanów.

## DP-01 — największa suma bez sąsiadujących indeksów

**Poziom:** L1
**Wymagania:** [Karta techniki](../patterns/14-dynamic-programming.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Ostatni element możesz wybrać albo pominąć.

</details>

<details>
<summary>Wskazówka 2</summary>

Wybór wyklucza sąsiada.

</details>

<details>
<summary>Wskazówka 3</summary>

Wystarczą dwa poprzednie wyniki prefiksów z bazą dopuszczającą pusty wybór.

</details>

**Treść i kontrakt.** Wybierz podzbiór indeksów tablicy int tak, aby żadne dwa
wybrane indeksy nie były sąsiadami. Zwróć maksymalną sumę jako long. Wolno nie
wybrać niczego, więc wynik dla pustego lub całkowicie ujemnego wejścia wynosi 0.
n w 0..100 000; nie zmieniaj wejścia. `[2,7,9,3,1]` → 12.
Docelowo O(n) czasu, O(1) pamięci pomocniczej.

Kontekst: nierozwiązany szkielet.

<!-- exercise:DP-01 -->
```java
static long maxNonAdjacentSum(int[] values) {
    throw new UnsupportedOperationException("TODO DP-01");
}
```

Kontekst: wykonywalne testy.

<!-- tests:DP-01 -->
```java
static void verify() {
    int[] input = {2, 7, 9, 3, 1};
    assert maxNonAdjacentSum(input) == 12;
    assert Arrays.equals(input, new int[] {2, 7, 9, 3, 1});
    assert maxNonAdjacentSum(new int[] {2, 1, 1, 2}) == 4;
    assert maxNonAdjacentSum(new int[] {-2, -1}) == 0;
    assert maxNonAdjacentSum(new int[] {-2, 5, -1, 6}) == 11;
    assert maxNonAdjacentSum(new int[] {8}) == 8;
    assert maxNonAdjacentSum(new int[0]) == 0;
    assert maxNonAdjacentSum(new int[] {Integer.MAX_VALUE, 0, Integer.MAX_VALUE}) == 4294967294L;

    Random random = new Random(1401);
    for (int trial = 0; trial < 80; trial++) {
        int[] values = random.ints(10, -5, 10).toArray();
        long expected = 0;
        for (int mask = 0; mask < (1 << values.length); mask++) {
            if ((mask & (mask << 1)) != 0) { continue; }
            long sum = 0;
            for (int i = 0; i < values.length; i++) { if ((mask & (1 << i)) != 0) { sum += values[i]; } }
            expected = Math.max(expected, sum);
        }
        assert maxNonAdjacentSum(values) == expected : "seed=1401 trial=" + trial;
    }
}
```

## DP-02 — liczba dróg w prostokątnej siatce

**Poziom:** L1
**Wymagania:** [Karta techniki](../patterns/14-dynamic-programming.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Do pola prowadzi ruch z góry albo z lewej.

</details>

<details>
<summary>Wskazówka 2</summary>

Stan startu ma jeden pusty sposób dla dodatnich wymiarów.

</details>

<details>
<summary>Wskazówka 3</summary>

Przetwarzaj wiersze używając tablicy kolumn; zerowy wymiar nie ma pola startu.

</details>

**Treść i kontrakt.** W siatce rows × columns zaczynasz w lewym górnym polu
i kończysz w prawym dolnym. Każdy ruch prowadzi o jedno pole w prawo albo w dół.
Policz różne drogi jako long. Wymiary w 0..20; zerowy wymiar → 0 dróg, siatka
1×1 → 1 droga bez ruchów. Dla tego zakresu wynik mieści się w long, nie zawsze
w int. Rozwiąż przez DP w O(rows·columns) czasu dla dodatnich wymiarów,
z pamięcią O(columns) lub O(min(rows,columns)).

Kontekst: nierozwiązany szkielet.

<!-- exercise:DP-02 -->
```java
static long countGridPaths(int rows, int columns) {
    throw new UnsupportedOperationException("TODO DP-02");
}
```

Kontekst: wykonywalne testy baz i symetrii.

<!-- tests:DP-02 -->
```java
static void verify() {
    assert countGridPaths(3, 7) == 28;
    assert countGridPaths(7, 3) == 28;
    assert countGridPaths(1, 1) == 1;
    assert countGridPaths(1, 9) == 1;
    assert countGridPaths(9, 1) == 1;
    assert countGridPaths(0, 3) == 0;
    assert countGridPaths(3, 0) == 0;
    assert countGridPaths(0, 0) == 0;
    assert countGridPaths(20, 20) == 35_345_263_800L;
}
```

## DP-03 — odległość edycyjna

**Poziom:** L3
**Wymagania:** [Karta techniki](../patterns/14-dynamic-programming.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Stan opisuje przekształcenie dwóch prefiksów.

</details>

<details>
<summary>Wskazówka 2</summary>

Ostatnia operacja to wstawienie, usunięcie lub zamiana.

</details>

<details>
<summary>Wskazówka 3</summary>

Zainicjalizuj pusty wiersz i kolumnę; rozważ minimum wszystkich ostatnich decyzji.

</details>

**Treść i kontrakt.** Dla dwóch napisów ASCII zwróć minimalną liczbę operacji,
aby przekształcić pierwszy w drugi. Operacja wstawia jeden znak, usuwa jeden znak
albo zamienia jeden znak na inny; każda kosztuje 1. Zamiana kolejności sąsiadów
nie jest pojedynczą operacją. Wielkość liter ma znaczenie. Długości 0..1000.
`kitten` → `sitting` kosztuje 3. Docelowo O((n + 1)(m + 1)) czasu; pamięć
O((n + 1)(m + 1)), opcjonalnie zredukuj ją do liniowej.

Kontekst: nierozwiązany szkielet.

<!-- exercise:DP-03 -->
```java
static int editDistance(String first, String second) {
    throw new UnsupportedOperationException("TODO DP-03");
}
```

Kontekst: wykonywalne testy odróżniają odległość od liczby nierównych pozycji.

<!-- tests:DP-03 -->
```java
static void verify() {
    assert editDistance("kitten", "sitting") == 3;
    assert editDistance("horse", "ros") == 3;
    assert editDistance("", "abc") == 3;
    assert editDistance("abc", "") == 3;
    assert editDistance("", "") == 0;
    assert editDistance("same", "same") == 0;
    assert editDistance("a", "A") == 1;
    assert editDistance("ab", "ba") == 2;
    assert editDistance("abc", "yabd") == 2;
    assert editDistance("sitting", "kitten") == 3;
}
```
