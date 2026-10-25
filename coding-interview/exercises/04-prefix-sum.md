# Zadania: prefix sum

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/04-prefix-sum.md)

## PRE-01 — wiele pytań o sumę zakresu

**Poziom:** L1
**Wymagania:** [Karta techniki](../patterns/04-prefix-sum.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Zapytania współdzielą sumy tych samych początków.

</details>

<details>
<summary>Wskazówka 2</summary>

Zapisz sumę pierwszych i elementów.

</details>

<details>
<summary>Wskazówka 3</summary>

Pusty prefiks ma sumę zero; zakres jest różnicą dwóch prefiksów.

</details>

**Treść i kontrakt.** Dla tablicy int i zapytań `[left,right)` zwróć tablicę long
z sumami w kolejności zapytań. `0 <= left <= right <= n`; pusty zakres ma sumę 0.
Wartości mogą być ujemne, n i liczba zapytań w 0..100 000. Nie zmieniaj wejść.
`[2,-1,4]`, zapytania `[[0,3],[1,3],[2,2]]` → `[5,3,0]`.
Docelowo O(n + q) czasu i O(n) pamięci pomocniczej poza wynikiem O(q).

Kontekst: nierozwiązany szkielet.

<!-- exercise:PRE-01 -->
```java
static long[] rangeSums(int[] values, int[][] queries) {
    throw new UnsupportedOperationException("TODO PRE-01");
}
```

Kontekst: wykonywalne testy.

<!-- tests:PRE-01 -->
```java
static void verify() {
    int[] values = {2, -1, 4};
    int[][] queries = {{0, 3}, {1, 3}, {2, 2}, {0, 1}, {3, 3}};
    assert Arrays.equals(rangeSums(values, queries), new long[] {5, 3, 0, 2, 0});
    assert Arrays.equals(values, new int[] {2, -1, 4});
    assert Arrays.deepEquals(queries, new int[][] {{0, 3}, {1, 3}, {2, 2}, {0, 1}, {3, 3}});
    assert Arrays.equals(rangeSums(new int[0], new int[][] {{0, 0}}), new long[] {0});
    assert rangeSums(new int[] {1}, new int[0][]).length == 0;
    assert Arrays.equals(rangeSums(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE},
            new int[][] {{0, 2}}), new long[] {4294967294L});
}
```

## PRE-02 — najdłuższy zrównoważony fragment binarny

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/04-prefix-sum.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Równość zer i jedynek można wyrazić bilansem.

</details>

<details>
<summary>Wskazówka 2</summary>

Dwa identyczne bilanse wyznaczają zrównoważony fragment.

</details>

<details>
<summary>Wskazówka 3</summary>

Dla maksymalnej długości zapamiętaj najwcześniejszy indeks bilansu, także pustego.

</details>

**Treść i kontrakt.** Tablica zawiera wyłącznie 0 i 1. Zwróć długość najdłuższego
spójnego fragmentu o równej liczbie zer i jedynek. Jeśli takiego nie ma, zwróć 0.
Długość 0..100 000. Nie zmieniaj wejścia. `[0,1,0]` → 2.
Docelowo O(n) czasu, O(n) pamięci.

Kontekst: nierozwiązany szkielet.

<!-- exercise:PRE-02 -->
```java
static int longestBalancedBinary(int[] values) {
    throw new UnsupportedOperationException("TODO PRE-02");
}
```

Kontekst: wykonywalne testy.

<!-- tests:PRE-02 -->
```java
static void verify() {
    int[] input = {0, 0, 1, 0, 1, 1, 0};
    assert longestBalancedBinary(input) == 6;
    assert Arrays.equals(input, new int[] {0, 0, 1, 0, 1, 1, 0});
    assert longestBalancedBinary(new int[] {0, 1, 0}) == 2;
    assert longestBalancedBinary(new int[] {1, 0}) == 2;
    assert longestBalancedBinary(new int[] {1, 1, 1}) == 0;
    assert longestBalancedBinary(new int[] {0}) == 0;
    assert longestBalancedBinary(new int[0]) == 0;

    Random random = new Random(402);
    for (int trial = 0; trial < 150; trial++) {
        int[] values = random.ints(12, 0, 2).toArray();
        int expected = 0;
        for (int left = 0; left < values.length; left++) {
            int zeros = 0, ones = 0;
            for (int right = left; right < values.length; right++) {
                if (values[right] == 0) { zeros++; } else { ones++; }
                if (zeros == ones) { expected = Math.max(expected, right - left + 1); }
            }
        }
        assert longestBalancedBinary(values) == expected : "seed=402 trial=" + trial;
    }
}
```

## PRE-03 — fragmenty o sumie podzielnej przez d

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/04-prefix-sum.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Podzielność różnicy zależy od reszt prefiksów.

</details>

<details>
<summary>Wskazówka 2</summary>

Zliczaj wcześniejsze prefiksy o tej samej reszcie.

</details>

<details>
<summary>Wskazówka 3</summary>

Normalizuj ujemne reszty i zapisuj bieżący stan po odczycie wcześniejszych.

</details>

**Treść i kontrakt.** Policz niepuste spójne fragmenty tablicy int, których suma
jest podzielna przez dodatnie d. Wartości mogą być ujemne; n w 0..100 000,
`1 <= d <= 100 000`. Wynik long, bez mutacji. Dla `[4,5,0,-2,-3,1]`, d = 5
wynik wynosi 7. Docelowo O(n + d) czasu i O(d) pamięci albo oczekiwane O(n)
z pamięcią O(min(n + 1, d)). Ujemna reszta Javy nie zmienia matematycznej podzielności.

Kontekst: nierozwiązany szkielet.

<!-- exercise:PRE-03 -->
```java
static long countDivisible(int[] values, int d) {
    throw new UnsupportedOperationException("TODO PRE-03");
}
```

Kontekst: wykonywalne testy.

<!-- tests:PRE-03 -->
```java
static void verify() {
    assert countDivisible(new int[] {4, 5, 0, -2, -3, 1}, 5) == 7;
    assert countDivisible(new int[] {-1, 5}, 5) == 1;
    assert countDivisible(new int[] {0, 0}, 3) == 3;
    assert countDivisible(new int[] {1, 2, 3}, 1) == 6;
    assert countDivisible(new int[] {1}, 2) == 0;
    assert countDivisible(new int[0], 7) == 0;
    assert countDivisible(new int[100_000], 1) == 5_000_050_000L;
}
```
