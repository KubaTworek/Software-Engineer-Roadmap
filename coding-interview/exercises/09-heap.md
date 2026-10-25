# Zadania: heap i top-k

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/09-heap.md)

## HEAP-01 — k największych w ustalonej kolejności

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/09-heap.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Nie potrzebujesz sortować całego wejścia.

</details>

<details>
<summary>Wskazówka 2</summary>

Przechowuj k największych z najgorszym kandydatem na szczycie.

</details>

<details>
<summary>Wskazówka 3</summary>

Odczytane minimum umieszczaj od końca wyniku, zachowując duplikaty.

</details>

**Treść i kontrakt.** Zwróć k największych elementów tablicy int w kolejności
nierosnącej. Powtórzenia liczą się osobno. `0 <= k <= n <= 100 000`; k = 0 daje
pustą tablicę. Nie zmieniaj wejścia. `[3,1,5,5,2]`, k = 3 → `[5,5,3]`.
Docelowo O(n log(k + 1) + k log(k + 1)) czasu i O(k) pamięci.

Kontekst: nierozwiązany szkielet.

<!-- exercise:HEAP-01 -->
```java
static int[] kLargest(int[] values, int k) {
    throw new UnsupportedOperationException("TODO HEAP-01");
}
```

Kontekst: wykonywalne testy.

<!-- tests:HEAP-01 -->
```java
static void verify() {
    int[] input = {3, 1, 5, 5, 2};
    assert Arrays.equals(kLargest(input, 3), new int[] {5, 5, 3});
    assert Arrays.equals(input, new int[] {3, 1, 5, 5, 2});
    assert Arrays.equals(kLargest(new int[] {-3, -1, -2}, 2), new int[] {-1, -2});
    assert Arrays.equals(kLargest(new int[] {2, 1}, 2), new int[] {2, 1});
    assert kLargest(new int[] {1, 2}, 0).length == 0;
    assert kLargest(new int[0], 0).length == 0;
    assert Arrays.equals(kLargest(new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE}, 1),
            new int[] {Integer.MAX_VALUE});
}
```

## HEAP-02 — najbliższe punkty z remisami

**Poziom:** L3
**Wymagania:** [Karta techniki](../patterns/09-heap.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Kwadrat odległości wystarcza do porównania.

</details>

<details>
<summary>Wskazówka 2</summary>

Pełny porządek to odległość, x, następnie y.

</details>

<details>
<summary>Wskazówka 3</summary>

Kopiec ogranicz do k z najgorszym na szczycie; uporządkuj końcowy wynik.

</details>

**Treść i kontrakt.** Dla punktów `[x,y]` zwróć k najbliższych początkowi układu,
uporządkowanych po rosnącej odległości, następnie po x, następnie po y. Duplikaty
punktów zachowaj. Współrzędne są w [-1 000 000 000, 1 000 000 000],
`0 <= k <= n <= 100 000`. Kwadrat odległości mieści się w long. Nie zmieniaj
wejścia; wynik ma własne wiersze. Docelowo O(n log(k + 1) + k log(k + 1)) czasu,
O(k) pamięci. Nie potrzebujesz pierwiastków do porównywania odległości.

Kontekst: nierozwiązany szkielet.

<!-- exercise:HEAP-02 -->
```java
static int[][] closestPoints(int[][] points, int k) {
    throw new UnsupportedOperationException("TODO HEAP-02");
}
```

Kontekst: wykonywalne testy deterministycznych remisów i kopii.

<!-- tests:HEAP-02 -->
```java
static void verify() {
    int[][] input = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {9, 9}};
    int[][] result = closestPoints(input, 3);
    assert Arrays.deepEquals(result, new int[][] {{-1, 0}, {0, -1}, {0, 1}});
    result[0][0] = 99;
    assert Arrays.deepEquals(input, new int[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {9, 9}});
    assert Arrays.deepEquals(closestPoints(new int[][] {{1, 1}, {1, 1}, {0, 0}}, 3),
            new int[][] {{0, 0}, {1, 1}, {1, 1}});
    assert closestPoints(new int[0][], 0).length == 0;
    assert closestPoints(new int[][] {{1, 2}}, 0).length == 0;
    assert Arrays.deepEquals(closestPoints(new int[][] {{1_000_000_000, 1_000_000_000}, {3, 4}}, 1),
            new int[][] {{3, 4}});
}
```

## HEAP-03 — scal wiele uporządkowanych źródeł

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/09-heap.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Następny wynik jest głową jednego ze źródeł.

</details>

<details>
<summary>Wskazówka 2</summary>

Przechowuj w kopcu aktualną głowę każdego niepustego źródła.

</details>

<details>
<summary>Wskazówka 3</summary>

Po zdjęciu wpisu dodaj wyłącznie jego następnik, pamiętając źródło i indeks.

</details>

**Treść i kontrakt.** Każdy wiersz int[][] jest niemalejącą tablicą, może być
pusty. Scal wszystkie wartości do jednej niemalejącej tablicy, zachowując
duplikaty. Liczba źródeł k w 0..10 000, suma ich długości N w 0..100 000.
Nie zmieniaj źródeł. Docelowo O(k + N log(k + 1)) czasu i O(k) pamięci pomocniczej
poza wynikiem O(N). Gdy wszystkie źródła są puste, zwróć pustą tablicę.

Kontekst: nierozwiązany szkielet.

<!-- exercise:HEAP-03 -->
```java
static int[] mergeSortedArrays(int[][] sources) {
    throw new UnsupportedOperationException("TODO HEAP-03");
}
```

Kontekst: wykonywalne testy.

<!-- tests:HEAP-03 -->
```java
static void verify() {
    int[][] input = {{1, 4, 5}, {}, {1, 3, 4}, {2, 6}};
    assert Arrays.equals(mergeSortedArrays(input), new int[] {1, 1, 2, 3, 4, 4, 5, 6});
    assert Arrays.deepEquals(input, new int[][] {{1, 4, 5}, {}, {1, 3, 4}, {2, 6}});
    assert mergeSortedArrays(new int[0][]).length == 0;
    assert mergeSortedArrays(new int[][] {{}, {}}).length == 0;
    assert Arrays.equals(mergeSortedArrays(new int[][] {{-3, -1}, {-2, 0}}),
            new int[] {-3, -2, -1, 0});
    assert Arrays.equals(mergeSortedArrays(new int[][] {{Integer.MAX_VALUE}, {Integer.MIN_VALUE}}),
            new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE});
    int[][] one = {{1}};
    int[] result = mergeSortedArrays(one);
    result[0] = 99;
    assert one[0][0] == 1;
}
```
