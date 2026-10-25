# Zadania: two pointers

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/02-two-pointers.md)

## TWO-01 — unikalne wartości uporządkowanego wejścia

**Poziom:** L1
**Wymagania:** [Karta techniki](../patterns/02-two-pointers.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Równe wartości w uporządkowanym wejściu są sąsiadami.

</details>

<details>
<summary>Wskazówka 2</summary>

Każda grupa wnosi jedną wartość.

</details>

<details>
<summary>Wskazówka 3</summary>

Najpierw możesz policzyć grupy, potem wypełnić wynik dokładnego rozmiaru.

</details>

**Treść i kontrakt.** Z niemalejącej tablicy int zwróć nową tablicę wszystkich
różnych wartości, nadal niemalejącą. Nie zmieniaj wejścia. Długość 0..100 000.
`[1,1,2,2,4]` → `[1,2,4]`. Puste wejście daje pustą tablicę. Docelowo O(n) czasu,
O(1) pamięci pomocniczej poza tablicą wyniku O(u), gdzie u jest liczbą różnych wartości.

Kontekst: nierozwiązany szkielet.

<!-- exercise:TWO-01 -->
```java
static int[] uniqueSorted(int[] sorted) {
    throw new UnsupportedOperationException("TODO TWO-01");
}
```

Kontekst: wykonywalne testy.

<!-- tests:TWO-01 -->
```java
static void verify() {
    int[] input = {1, 1, 2, 2, 4};
    assert Arrays.equals(uniqueSorted(input), new int[] {1, 2, 4});
    assert Arrays.equals(input, new int[] {1, 1, 2, 2, 4});
    assert Arrays.equals(uniqueSorted(new int[] {-2, -2, 0, 3}), new int[] {-2, 0, 3});
    assert Arrays.equals(uniqueSorted(new int[] {5, 5, 5}), new int[] {5});
    assert Arrays.equals(uniqueSorted(new int[] {7}), new int[] {7});
    assert uniqueSorted(new int[0]).length == 0;
    int[] single = {8};
    int[] result = uniqueSorted(single);
    result[0] = 9;
    assert single[0] == 8;
}
```

## TWO-02 — uporządkowane kwadraty

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/02-two-pointers.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Największy kwadrat znajduje się na jednym z końców.

</details>

<details>
<summary>Wskazówka 2</summary>

Wypełniaj wynik od największych pozycji.

</details>

<details>
<summary>Wskazówka 3</summary>

Przejdź na long przed mnożeniem; abs MIN_VALUE w int nie daje dodatniej wartości.

</details>

**Treść i kontrakt.** Dla niemalejącej tablicy int zwróć niemalejącą tablicę long
z kwadratami wszystkich elementów. Dopuszczalne są ujemne liczby i granice int;
wynik ma zachować duplikaty. Długość 0..100 000. Nie zmieniaj wejścia.
`[-4,-1,0,3,10]` → `[0,1,9,16,100]`. Docelowo O(n) czasu i O(1) dodatkowej
pamięci poza wynikiem O(n).

Kontekst: nierozwiązany szkielet.

<!-- exercise:TWO-02 -->
```java
static long[] sortedSquares(int[] sorted) {
    throw new UnsupportedOperationException("TODO TWO-02");
}
```

Kontekst: wykonywalne testy.

<!-- tests:TWO-02 -->
```java
static void verify() {
    int[] input = {-4, -1, 0, 3, 10};
    assert Arrays.equals(sortedSquares(input), new long[] {0, 1, 9, 16, 100});
    assert Arrays.equals(input, new int[] {-4, -1, 0, 3, 10});
    assert Arrays.equals(sortedSquares(new int[] {-5, -2}), new long[] {4, 25});
    assert Arrays.equals(sortedSquares(new int[] {-2, 2}), new long[] {4, 4});
    assert sortedSquares(new int[0]).length == 0;
    assert Arrays.equals(sortedSquares(new int[] {0}), new long[] {0});
    assert Arrays.equals(sortedSquares(new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE}),
            new long[] {4611686014132420609L, 4611686018427387904L});
}
```

## TWO-03 — liczba par indeksów o zadanej sumie

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/02-two-pointers.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Liczą się pary indeksów, nie tylko pary wartości.

</details>

<details>
<summary>Wskazówka 2</summary>

Różne końcowe wartości dają iloczyn liczności grup.

</details>

<details>
<summary>Wskazówka 3</summary>

Równe końce wymagają liczby par wewnątrz jednej grupy, liczonej w long.

</details>

**Treść i kontrakt.** W niemalejącej tablicy int policz wszystkie pary indeksów
`i < j`, których matematyczna suma jest równa target typu int. Różne indeksy
liczą się osobno, także dla równych wartości. Długość 0..100 000. Wynik long,
bez mutacji. `[1,1,2,2,2]`, target 3 → 6. Docelowo O(n) czasu i O(1) pamięci.

Kontekst: nierozwiązany szkielet.

<!-- exercise:TWO-03 -->
```java
static long countPairs(int[] sorted, int target) {
    throw new UnsupportedOperationException("TODO TWO-03");
}
```

Kontekst: wykonywalne testy duplikatów, pustego wyniku i przepełnienia.

<!-- tests:TWO-03 -->
```java
static void verify() {
    assert countPairs(new int[] {1, 1, 2, 2, 2}, 3) == 6;
    assert countPairs(new int[] {2, 2, 2, 2}, 4) == 6;
    assert countPairs(new int[] {-3, -1, 0, 1, 3}, 0) == 2;
    assert countPairs(new int[] {1}, 2) == 0;
    assert countPairs(new int[0], 0) == 0;
    assert countPairs(new int[] {1, 2, 3}, 9) == 0;
    assert countPairs(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, -2) == 0;
    assert countPairs(new int[100_000], 0) == 4_999_950_000L;

    Random random = new Random(203);
    for (int trial = 0; trial < 200; trial++) {
        int[] values = random.ints(12, -4, 5).sorted().toArray();
        int target = random.nextInt(13) - 6;
        long expected = 0;
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                if ((long) values[i] + values[j] == target) { expected++; }
            }
        }
        assert countPairs(values, target) == expected : "seed=203 trial=" + trial;
    }
}
```
