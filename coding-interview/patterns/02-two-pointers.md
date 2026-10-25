# 02. Two pointers — odrzucaj całe grupy kandydatów

[Katalog](../README.md) · [Hash map](01-hash-map.md)

## Problem i kontrakt

Dla niemalejącej tablicy `int[] sorted` znajdź dwa różne indeksy o sumie równej
`int target`. Wynik jest indeksowany od zera; brak pary to pusta tablica.
Duplikaty są dozwolone. Wejście pozostaje bez zmian.

## Od brute force do niezmiennika

Wszystkie pary kosztują O(n²). Sortowanie wejścia jest już warunkiem kontraktu,
więc najmniejszy i największy kandydat pozwalają odrzucić cały wiersz par.

**Niezmiennik:** jeżeli rozwiązanie nie zostało znalezione, istniejąca para nadal
mieści się między `left` a `right`. Gdy suma jest za mała, żadna para zaczynająca
się w `left` i kończąca nie dalej niż `right` nie osiągnie celu. Wolno zwiększyć
`left`. Dla sumy za dużej analogicznie zmniejszamy `right`. Zakres maleje co krok.

## Przykład krok po kroku

`[1, 2, 4, 7, 11]`, cel `9`: suma `1 + 11 = 12` usuwa prawy koniec;
`1 + 7 = 8` usuwa lewy; `2 + 7 = 9` daje `[1, 3]`.
Każdy ruch wyklucza pary na podstawie porządku, nie zgadywania kierunku.

## Kod Java

Kontekst: składowa klasy. Sumę obliczamy w long.

```java
static int[] sortedTwoSum(int[] sorted, int target) {
    int left = 0;
    int right = sorted.length - 1;
    while (left < right) {
        long sum = (long) sorted[left] + sorted[right];
        if (sum == target) {
            return new int[] {left, right};
        }
        if (sum < target) {
            left++;
        } else {
            right--;
        }
    }
    return new int[0];
}
```

## Koszt i granice techniki

Czas O(n), pamięć pomocnicza O(1), wynik O(1). Sprawdzenie, czy tablica jest
posortowana, nie jest częścią metody. Dla nieposortowanej tablicy sortowanie
kosztuje O(n log n) i zmienia indeksy; aby zachować oryginalne, trzeba zapisać je
obok wartości albo użyć mapy. Istnieją też inne warianty two pointers, np.
kompaktowanie tablicy, ale wymagają własnego niezmiennika.

## Kontrprzykład i scenariusze

Nieposortowane `[3, 2, 4]`, cel `6`: porównanie `3 + 4` odrzuci `4`, choć
`2 + 4` jest rozwiązaniem. Bez porządku uzasadnienie ruchu wskaźnika znika.

Kontekst: wykonywane asercje, wyłącznie wejścia spełniające kontrakt.

```java
static void verify() {
    assert Arrays.equals(sortedTwoSum(new int[] {1, 2, 4, 7, 11}, 9),
            new int[] {1, 3});
    assert Arrays.equals(sortedTwoSum(new int[] {3, 3}, 6), new int[] {0, 1});
    assert sortedTwoSum(new int[] {3}, 6).length == 0;
    assert sortedTwoSum(new int[0], 0).length == 0;
    assert sortedTwoSum(new int[] {1, 2}, 9).length == 0;
    assert sortedTwoSum(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, -2).length == 0;
}
```

## Ćwiczenie

Rozwiąż 3Sum: posortuj, ustal pierwszy element i uruchom dwa wskaźniki na reszcie.
Wyjaśnij pomijanie duplikatów oraz koszt O(n²) po sortowaniu.
