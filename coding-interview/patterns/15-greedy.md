# 15. Greedy — lokalny wybór z argumentem wymiany

[Katalog](../README.md) · [Przedziały](06-intervals.md) · [DP](14-dynamic-programming.md)

## Problem i kontrakt

Wybierz maksymalną liczbę niepokrywających się spotkań. Każdy przedział int jest
półotwarty `[start,end)`, z `start < end`. Spotkanie zaczynające się w chwili
zakończenia poprzedniego jest dozwolone. Każde spotkanie ma jednakową wartość 1.
Zwracamy tylko liczbę, dla pustego wejścia 0. Nie zmieniamy wejścia.

## Od brute force do niezmiennika

Można sprawdzić wszystkie podzbiory i odrzucić kolidujące. Zamiast tego sortujemy
po końcu i wybieramy najwcześniej kończące się spotkanie zgodne z poprzednimi.

**Argument wymiany:** niech g kończy się najwcześniej, a o będzie pierwszym
spotkaniem pewnego rozwiązania optymalnego. Zamiana o na g nie zabierze miejsca
żadnemu kolejnemu spotkaniu, bo koniec g nie jest późniejszy niż koniec o.
Istnieje więc optimum zaczynające się od g. Powtarzamy argument dla spotkań po g.

**Niezmiennik:** po każdym wyborze istnieje rozwiązanie optymalne zawierające
dotychczasowy prefiks wyborów. To uzasadnienie greedy; sama intuicja „zostawiam
dużo miejsca” nie wystarcza dla innych celów, np. maksymalizacji wartości.

## Przykład krok po kroku

`[[0,5], [1,2], [2,3], [3,4]]` po sortowaniu po końcu zaczyna się od `[1,2]`.
Wybieramy go, potem `[2,3]` i `[3,4]`; `[0,5]` koliduje. Wynik 3.
Wybór najwcześniejszego początku dałby tylko `[0,5]` i wynik 1.

## Kod Java

Kontekst: składowa klasy, `java.util.*`. Kopia zewnętrznej tablicy wystarcza,
ponieważ sortowanie przestawia referencje, a wierszy nie modyfikujemy.

```java
static int maxMeetings(int[][] meetings) {
    int[][] sorted = meetings.clone();
    Arrays.sort(sorted, Comparator.comparingInt(meeting -> meeting[1]));
    int count = 0;
    long lastEnd = Long.MIN_VALUE;
    for (int[] meeting : sorted) {
        if (meeting[0] >= lastEnd) {
            count++;
            lastEnd = meeting[1];
        }
    }
    return count;
}
```

## Koszt i granice techniki

Czas O(n log n), pamięć pomocnicza O(n) na kopię i sortowanie, wynik O(1).
Przy już uporządkowanym wejściu wystarczy liniowy przegląd. Ta reguła rozwiązuje
maksymalizację liczby spotkań, nie minimalizację sal dla wszystkich spotkań.
Przedziały domknięte zmieniłyby warunek zgodności na ściśle późniejszy początek.

## Kontrprzykład i scenariusze

Dodaj wartości: `[0,2)` warte 2, `[2,4)` warte 2, `[0,4)` warte 10.
Najwcześniejszy koniec daje wartość 4, choć optimum to 10. Ważone planowanie
wymaga innego rozumowania, np. DP. Podobnie greedy największego nominału zawodzi
dla monet `[1,3,4]` i kwoty 6.

Kontekst: wykonywane asercje dla punktów styku i granic typu int.

```java
static void verify() {
    int[][] input = {{0, 5}, {1, 2}, {2, 3}, {3, 4}};
    assert maxMeetings(input) == 3;
    assert Arrays.deepEquals(input, new int[][] {{0, 5}, {1, 2}, {2, 3}, {3, 4}});
    assert maxMeetings(new int[][] {{1, 2}, {2, 3}}) == 2;
    assert maxMeetings(new int[][] {{1, 3}, {1, 3}}) == 1;
    assert maxMeetings(new int[0][]) == 0;
    assert maxMeetings(new int[][] {{Integer.MIN_VALUE, -1}, {-1, Integer.MAX_VALUE}}) == 2;
}
```

## Ćwiczenie

Zwróć indeksy wybranych spotkań w oryginalnym wejściu. Następnie dla małych
losowych danych porównaj liczność wyniku z enumeracją wszystkich podzbiorów.
