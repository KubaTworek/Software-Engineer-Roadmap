# 04. Prefix sum — zamień zakres na różnicę dwóch stanów

[Katalog](../README.md) · [Hash map](01-hash-map.md)

## Problem i kontrakt

Policz niepuste spójne fragmenty `int[] values` o sumie `int target`. Dozwolone są
liczby ujemne i zera. Zwracamy `long`, bo liczba fragmentów może przekroczyć int.
Puste wejście daje 0. Nie zmieniamy tablicy.

## Od brute force do niezmiennika

Rozszerzanie fragmentu dla każdego początku z bieżącą sumą kosztuje O(n²).
Niech `P[j]` oznacza sumę pierwszych `j` elementów, z `P[0] = 0`. Suma `[i, j)`
to `P[j] - P[i]`, więc szukamy wcześniejszych prefiksów `P[i] = P[j] - target`.

**Niezmiennik:** przed doliczeniem bieżącego prefiksu mapa zna liczności wszystkich
wcześniejszych prefiksów, w tym pustego. Najpierw odczyt, potem zwiększenie
licznika wyklucza pusty fragment, gdy target = 0. Liczności, zamiast zbioru,
zachowują różne początki dające tę samą sumę.

## Przykład krok po kroku

`[1, -1, 1]`, cel `1`: start `{0:1}`. Prefiks `1` znajduje jedno `0`, więc wynik
1. Prefiks `0` szuka `-1`, nic nie dodaje i zwiększa liczność zera do 2. Ostatni
prefiks `1` znajduje już dwa zera: wynik 3 (`[0,1)`, `[0,3)`, `[2,3)`).

## Kod Java

Kontekst: składowa klasy, `java.util.*`. Dla tablicy indeksowanej int sumy elementów
int i różnica z celem int mieszczą się w long; licznik fragmentów również.

```java
static long countSubarrays(int[] values, int target) {
    Map<Long, Long> frequency = new HashMap<>();
    frequency.put(0L, 1L);
    long prefix = 0;
    long count = 0;
    for (int value : values) {
        prefix += value;
        count += frequency.getOrDefault(prefix - target, 0L);
        // Bieżący prefiks staje się początkiem dopiero dla kolejnych końców.
        frequency.merge(prefix, 1L, Long::sum);
    }
    return count;
}
```

## Koszt i granice techniki

Oczekiwane O(n) czasu przy dobrym hashowaniu, O(n) pamięci pomocniczej, O(1)
wyniku. Osobna tablica sum prefiksowych daje O(1) na statyczne zapytanie o sumę
zakresu po O(n) przygotowaniu; mapa w tej karcie służy liczeniu zakresów.
Częste aktualizacje wejścia wymagają innej struktury, np. drzewa Fenwicka.

## Kontrprzykład i scenariusze

Brak początkowego `0 → 1` pomija fragment zaczynający się na indeksie 0.
Zbiór zamiast liczników dla `[0, 0]`, cel `0`, nie policzy wszystkich 3 fragmentów.
Przy ujemnych liczbach sama suma okna nie wyznacza bezpiecznego ruchu granicy.

Kontekst: wykonywane asercje, także porównanie z prostym brute force.

```java
static void verify() {
    assert countSubarrays(new int[] {1, -1, 1}, 1) == 3;
    assert countSubarrays(new int[] {0, 0}, 0) == 3;
    assert countSubarrays(new int[] {2}, 2) == 1;
    assert countSubarrays(new int[0], 0) == 0;
    Random random = new Random(42);
    for (int trial = 0; trial < 200; trial++) {
        int[] values = random.ints(8, -3, 4).toArray();
        int target = random.nextInt(7) - 3;
        long expected = 0;
        for (int left = 0; left < values.length; left++) {
            long sum = 0;
            for (int right = left; right < values.length; right++) {
                sum += values[right];
                if (sum == target) { expected++; }
            }
        }
        assert countSubarrays(values, target) == expected;
    }
}
```

## Ćwiczenie

Zwróć długość najdłuższego fragmentu o sumie k. Dlaczego mapa powinna zachować
najwcześniejszy indeks danego prefiksu zamiast jego ostatniego wystąpienia?
