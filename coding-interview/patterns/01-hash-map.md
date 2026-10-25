# 01. Hash map — pamiętaj wcześniej widziane wartości

[Katalog](../README.md) · [Two pointers](02-two-pointers.md)

## Problem i kontrakt

Two Sum: dla nieposortowanego `int[] values` i celu `int target` zwróć dwa różne
indeksy o zadanej sumie. Dowolna poprawna para wystarcza; brak pary oznacza puste
`int[]`. Nie zmieniamy wejścia. Sumę interpretujemy matematycznie, bez zawijania int.

## Od brute force do niezmiennika

Sprawdzenie każdej pary `i < j` zajmuje O(n²) czasu i O(1) pamięci pomocniczej.
Dla bieżącej wartości potrzebujemy tylko odpowiedzi: czy dopełnienie wystąpiło
wcześniej? Mapa zastępuje ponowne przechodzenie prefiksu tablicy.

**Niezmiennik:** przed krokiem `i` mapa przechowuje indeksy wyłącznie z `[0, i)`.
Najpierw szukamy dopełnienia, potem zapisujemy bieżący element. Wtedy para ma
różne indeksy, a każda istniejąca para zostanie rozpoznana przy późniejszym z nich.
Nadpisanie indeksu duplikatu jest dozwolone: szukamy dowolnej pary.

## Przykład krok po kroku

Dla `[3, 2, 4]`, cel `6`: zapisz `3 → 0`; przy `2` nie ma jeszcze `4`, więc
zapisz `2 → 1`; przy `4` znajdziesz `2 → 1` i zwrócisz `[1, 2]`.
Dla `[3, 3]` drugi element znajdzie pierwszy; pojedyncze `[3]` nie tworzy pary.

## Kod Java

Kontekst: składowa klasy; importy `java.util.*` zapewnia skrypt kontrolny.
Klucz `long` zapobiega przepełnieniu przy obliczaniu dopełnienia.

```java
static int[] twoSum(int[] values, int target) {
    Map<Long, Integer> seen = new HashMap<>();
    for (int i = 0; i < values.length; i++) {
        long complement = (long) target - values[i];
        Integer previous = seen.get(complement);
        if (previous != null) {
            return new int[] {previous, i};
        }
        // Zapis po odczycie chroni przed użyciem indeksu i dwa razy.
        seen.put((long) values[i], i);
    }
    return new int[0];
}
```

## Koszt i granice techniki

Oczekiwane O(n) czasu przy dobrym rozkładzie hashy i O(n) pamięci pomocniczej;
wynik O(1). To nie gwarancja najgorszego O(n). Gdy wejście jest posortowane,
two pointers pozwala zrezygnować z mapy. Gdy potrzebne są wszystkie pary,
pojedynczy indeks na wartość nie opisuje pełnego zbioru odpowiedzi.

## Kontrprzykład i scenariusze

Zapis przed odczytem dla `[3]`, cel `6`, błędnie dopuszcza `[0, 0]`.
Dla `[MAX_VALUE, MAX_VALUE]`, cel `-2`, dodawanie int tworzyłoby fałszywą parę.

Kontekst: wykonywane asercje w metodzie `verify()`.

```java
static void verify() {
    assert Arrays.equals(twoSum(new int[] {3, 2, 4}, 6), new int[] {1, 2});
    assert Arrays.equals(twoSum(new int[] {3, 3}, 6), new int[] {0, 1});
    assert twoSum(new int[] {3}, 6).length == 0;
    assert twoSum(new int[0], 0).length == 0;
    assert twoSum(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, -2).length == 0;
    assert twoSum(new int[] {-3, 5}, 2).length == 2;
}
```

## Ćwiczenie

Zwróć liczbę wszystkich par indeksów. Jak zmieni się wartość mapy i typ wyniku?
Sprawdź `[2, 2, 2]`, cel `4`: są trzy pary, nie jedna.
