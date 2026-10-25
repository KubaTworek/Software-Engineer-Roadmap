# 19. Monotonic deque — ekstremum przesuwanego okna

[Katalog](../README.md) · [Zadania](../exercises/19-monotonic-deque.md)

## Problem i kontrakt

Dla tablicy int zwróć maksimum każdego spójnego okna długości k. Wymagamy
1 ≤ k ≤ n, n ≥ 1. Wynik ma n-k+1 elementów; wejścia nie zmieniamy.

## Od brute force do niezmiennika

Skanowanie każdego okna kosztuje O(nk). Deque przechowuje indeksy kandydatów
rosnąco, a ich wartości malejąco. **Niezmiennik:** przód jest maksimum bieżącego
okna; żaden usunięty z tyłu element nie może być lepszy w przyszłym oknie niż
nowszy element o co najmniej takiej wartości. Nowszy pozostanie w oknie dłużej.
Stare indeksy usuwamy z przodu, kandydatów zdominowanych z tyłu.

## Przykład krok po kroku

`[1,3,-1,-3,5]`, k = 3 → `[3,3,5]`. Przy 3 usuwamy starsze 1 z tyłu.
Przy 5 usuwamy -3 i -1; stare 3 wypada z zakresu. Indeks mówi, kiedy wygasa kandydat.

## Kod Java

Kontekst: składowa klasy, java.util.*.

```java
static int[] windowMax(int[] values, int k) {
    int[] result = new int[values.length - k + 1];
    Deque<Integer> candidates = new ArrayDeque<>();
    for (int i = 0; i < values.length; i++) {
        // Przód wygasa przez indeks, tył odpada przez dominację wartości.
        while (!candidates.isEmpty() && candidates.peekFirst() <= i - k) { candidates.removeFirst(); }
        while (!candidates.isEmpty() && values[candidates.peekLast()] <= values[i]) { candidates.removeLast(); }
        candidates.addLast(i);
        if (i >= k - 1) { result[i - k + 1] = values[candidates.peekFirst()]; }
    }
    return result;
}
```

## Koszt i granice techniki

Czas O(n): każdy indeks jest dodany i usunięty najwyżej raz. Pamięć pomocnicza
O(k), wynik O(n-k+1). Sam stos nie usuwa wygasłych elementów z przodu.
To rozwiązanie ekstremum, nie mediany okna; dominacja nie zachowuje porządku statystycznego.

## Kontrprzykład i scenariusze

Bez wygaszania dla `[9,1,2]`, k = 2, otrzymasz błędnie drugie maksimum 9 zamiast 2.
Przechowywanie samych wartości komplikuje odróżnianie równych egzemplarzy.

Kontekst: wykonywane asercje i niezależne porównanie z O(nk).

```java
static void verify() {
    assert Arrays.equals(windowMax(new int[] {1, 3, -1, -3, 5}, 3), new int[] {3, 3, 5});
    assert Arrays.equals(windowMax(new int[] {9, 1, 2}, 2), new int[] {9, 2});
    Random random = new Random(190);
    for (int trial = 0; trial < 100; trial++) {
        int[] values = random.ints(10, -4, 5).toArray();
        int k = 1 + random.nextInt(values.length);
        int[] expected = new int[values.length - k + 1];
        for (int i = 0; i < expected.length; i++) {
            int best = Integer.MIN_VALUE;
            for (int j = i; j < i + k; j++) { best = Math.max(best, values[j]); }
            expected[i] = best;
        }
        assert Arrays.equals(windowMax(values, k), expected);
    }
}
```

## Ćwiczenie

Utrzymuj minimum i maksimum jednocześnie dla warunku ograniczonej różnicy w oknie.
