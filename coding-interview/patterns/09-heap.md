# 09. Heap / top-k — przechowuj tylko potrzebnych kandydatów

[Katalog](../README.md) · [Monotonic stack](07-monotonic-stack.md)

## Problem i kontrakt

Zwróć k-ty największy element niepustej tablicy int, licząc powtórzenia osobno.
Wymagamy `1 <= k <= values.length`; błędne k odrzucamy wyjątkiem. Dla `[5,5,3]`
i k = 2 wynik to 5, nie 3. Nie zmieniamy wejścia.

## Od brute force do niezmiennika

Sortowanie całej kopii kosztuje O(n log n) czasu oraz O(n) pamięci na kopię.
Nie potrzebujemy całego porządku, wystarczy pamiętać k największych kandydatów.

**Niezmiennik:** po każdym prefiksie kopiec zawiera min(k, liczba przetworzonych)
największych wartości z tego prefiksu. Dodanie wartości i usunięcie minimum przy
przekroczeniu k zachowuje ten warunek. Po końcu minimum z tych k wartości jest
k-tym największym elementem całego wejścia.

## Przykład krok po kroku

`[3, 2, 1, 5, 6, 4]`, k = 2. Zbiory kandydatów po kolejnych elementach:
`{3}`, `{2,3}`, `{2,3}`, `{3,5}`, `{5,6}`, `{5,6}`. Szczyt ma wartość 5.
To zapis zawartości, nie obietnica kolejności iterowania PriorityQueue.

## Kod Java

Kontekst: składowa klasy, `java.util.*`. Domyślny kopiec ma minimum na szczycie.

```java
static int kthLargest(int[] values, int k) {
    if (k < 1 || k > values.length) {
        throw new IllegalArgumentException("k poza zakresem");
    }
    PriorityQueue<Integer> largest = new PriorityQueue<>();
    for (int value : values) {
        largest.offer(value);
        if (largest.size() > k) {
            largest.poll();
        }
    }
    return largest.peek();
}
```

## Koszt i granice techniki

Czas O(n log(k + 1)), co uwzględnia k = 1; pamięć pomocnicza O(k), wynik O(1).
Wstawienie może chwilowo dać k + 1 elementów. Kopiec pasuje też do strumienia.
Quickselect daje inny kompromis: oczekiwane O(n) przy odpowiednim wyborze pivota,
ale zwykle zmienia tablicę i wymaga analizy najgorszego przypadku.

## Kontrprzykład i scenariusze

Max-heap z usuwaniem szczytu przy przepełnieniu zachowa k najmniejszych zamiast
największych. Dla `[3,2,1,5,6,4]`, k = 2, dałby granicę 2 zamiast 5.

Kontekst: wykonywane asercje z niezależnym porównaniem do sortowania kopii.

```java
static void verify() {
    assert kthLargest(new int[] {3, 2, 1, 5, 6, 4}, 2) == 5;
    assert kthLargest(new int[] {5, 5, 3}, 2) == 5;
    assert kthLargest(new int[] {-2}, 1) == -2;
    boolean rejected = false;
    try { kthLargest(new int[0], 1); }
    catch (IllegalArgumentException expected) { rejected = true; }
    assert rejected;
    Random random = new Random(45);
    for (int trial = 0; trial < 100; trial++) {
        int[] values = random.ints(10, -8, 9).toArray();
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        for (int k = 1; k <= values.length; k++) {
            assert kthLargest(values, k) == sorted[sorted.length - k];
        }
    }
}
```

## Ćwiczenie

Zwróć k najczęstszych wartości: mapa liczności i kopiec rozmiaru k. Ustal regułę
remisów i nie porównuj liczników odejmowaniem w komparatorze.
