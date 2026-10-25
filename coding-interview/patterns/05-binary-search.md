# 05. Binary search — znajdź granicę monotonicznego warunku

[Katalog](../README.md) · [Two pointers](02-two-pointers.md)

## Problem i kontrakt

Dla niemalejącego `int[] sorted` zwróć pierwszy indeks z wartością co najmniej
`target` (lower bound). Gdy nie ma takiego elementu, wynikiem jest długość tablicy.
Dla pustej tablicy wynik to 0. Duplikaty są dozwolone, wejście pozostaje bez zmian.

## Od brute force do niezmiennika

Przegląd liniowy szuka pierwszej pasującej wartości w O(n). Warunek
`sorted[i] >= target` ma postać: fałsz, fałsz, ..., prawda, prawda. Jedno sprawdzenie
pośrodku pozwala odrzucić połowę pozostałego obszaru.

**Niezmiennik:** indeksy `< left` mają wartości mniejsze od celu; indeksy `>= right`
mają wartości co najmniej równe celowi. Nierozstrzygnięty zakres to `[left, right)`.
Po zrównaniu granic `left` wskazuje pierwszy pasujący indeks lub koniec tablicy.
Każda aktualizacja skraca zakres, więc pętla się kończy.

## Przykład krok po kroku

`[1, 2, 2, 5]`, cel `2`: `[0,4)`, środek 2 pasuje → `right = 2`;
`[0,2)`, środek 1 pasuje → `right = 1`; `[0,1)`, środek 0 nie pasuje →
`left = 1`. Wynik 1, czyli początek duplikatów, a nie dowolna znaleziona dwójka.

## Kod Java

Kontekst: składowa klasy; bez dodatkowych importów.

```java
static int lowerBound(int[] sorted, int target) {
    int left = 0;
    int right = sorted.length;
    while (left < right) {
        int middle = left + (right - left) / 2;
        if (sorted[middle] < target) {
            left = middle + 1;
        } else {
            right = middle;
        }
    }
    return left;
}
```

## Koszt i granice techniki

Czas O(log n) dla niepustego wejścia, pamięć i wynik O(1). Metoda zakłada dostęp
po indeksie O(1). Sortowanie przed pojedynczym zapytaniem kosztowałoby więcej
niż przegląd liniowy i mogłoby zmienić znaczenie indeksów.

Wyszukiwanie po odpowiedzi jest rozszerzeniem: kandydat nie musi być elementem
tablicy, ale warunek wykonalności musi być monotoniczny. Jeśli pojedynczy test
kosztuje O(n), całość nie kosztuje samego O(log zakres).

## Kontrprzykład i scenariusze

Użycie `left = middle` dla `[1, 3]`, cel `2`, zostawia zakres `[0,1)` bez postępu.
Zwrócenie pierwszego trafienia wartości gubi kontrakt pierwszej pozycji duplikatów.

Kontekst: wykonywane asercje z porównaniem do liniowego szukania.

```java
static void verify() {
    assert lowerBound(new int[] {1, 2, 2, 5}, 2) == 1;
    assert lowerBound(new int[] {1, 2}, 3) == 2;
    assert lowerBound(new int[0], 7) == 0;
    Random random = new Random(43);
    for (int trial = 0; trial < 200; trial++) {
        int[] values = random.ints(12, -5, 6).sorted().toArray();
        int target = random.nextInt(15) - 7;
        int expected = 0;
        while (expected < values.length && values[expected] < target) {
            expected++;
        }
        assert lowerBound(values, target) == expected;
    }
}
```

## Ćwiczenie

Zapisz upper bound, czyli pierwszą pozycję z wartością większą od celu.
Wyznacz liczbę wystąpień jako różnicę obu granic, także przy braku wartości.
