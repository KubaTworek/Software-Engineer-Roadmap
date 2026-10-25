# 07. Monotonic stack — odkładaj nierozstrzygnięte indeksy

[Katalog](../README.md) · [Heap](09-heap.md)

## Problem i kontrakt

Dla każdej temperatury w `int[] temperatures` zwróć liczbę dni do pierwszego
późniejszego dnia o ściśle wyższej temperaturze. Brak takiego dnia daje 0.
Puste wejście daje pustą tablicę. Nie zmieniamy wejścia.

## Od brute force do niezmiennika

Dla każdego dnia szukanie cieplejszego w prawo kosztuje O(n²) w najgorszym
przypadku. Bieżący dzień może jednocześnie rozstrzygnąć wiele wcześniejszych.

**Niezmiennik:** stos zawiera tylko nierozstrzygnięte indeksy; od dołu do góry
ich temperatury są nierosnące. Gdy nowy dzień jest cieplejszy od szczytu,
zdejmujemy szczyt i zapisujemy odległość. To pierwszy cieplejszy dzień: gdyby
wcześniejszy istniał, indeks zostałby już zdjęty. Równe temperatury zostają.

## Przykład krok po kroku

`[73, 74, 74, 76]`: dzień 1 zdejmuje dzień 0 i zapisuje 1. Dzień 2 nie zdejmuje
dnia 1, bo 74 nie jest większe od 74. Dzień 3 zdejmuje dni 2 i 1, zapisując
odpowiednio 1 i 2. Wynik to `[1, 2, 1, 0]`.

## Kod Java

Kontekst: składowa klasy, `java.util.*`. Przechowujemy indeksy, aby policzyć odległość.

```java
static int[] warmerDays(int[] temperatures) {
    int[] wait = new int[temperatures.length];
    Deque<Integer> pending = new ArrayDeque<>();
    for (int day = 0; day < temperatures.length; day++) {
        while (!pending.isEmpty() && temperatures[day] > temperatures[pending.peek()]) {
            int previous = pending.pop();
            wait[previous] = day - previous;
        }
        pending.push(day);
    }
    return wait;
}
```

## Koszt i granice techniki

Całość O(n) czasu: każdy indeks trafia na stos raz i jest zdejmowany najwyżej
raz. Zagnieżdżona pętla nie powtarza O(n) pracy dla każdego dnia. Pamięć
pomocnicza O(n), wynik O(n). Wariant „większy lub równy” wymaga innego porównania;
kolejność i znaczenie równych elementów zależą od kontraktu zadania.

## Kontrprzykład i scenariusze

Warunek `>=` błędnie da wynik `[1,0]` dla `[74,74]`; poprawne jest `[0,0]`.
Stos samych temperatur nie wystarcza do wyliczenia liczby dni.

Kontekst: wykonywane asercje z porównaniem do szukania liniowego dla każdego dnia.

```java
static void verify() {
    assert Arrays.equals(warmerDays(new int[] {73, 74, 74, 76}), new int[] {1, 2, 1, 0});
    assert Arrays.equals(warmerDays(new int[] {74, 74}), new int[] {0, 0});
    assert warmerDays(new int[0]).length == 0;
    Random random = new Random(44);
    for (int trial = 0; trial < 200; trial++) {
        int[] values = random.ints(10, 0, 6).toArray();
        int[] expected = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                if (values[j] > values[i]) { expected[i] = j - i; break; }
            }
        }
        assert Arrays.equals(warmerDays(values), expected);
    }
}
```

## Ćwiczenie

Znajdź następny większy element w tablicy cyklicznej. Uzasadnij dwa przebiegi
i wyjaśnij, w którym z nich wolno dopisywać indeksy do stosu.
