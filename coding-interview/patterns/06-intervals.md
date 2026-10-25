# 06. Sortowanie i przedziały — uporządkuj granice

[Katalog](../README.md) · [Greedy](15-greedy.md)

## Problem i kontrakt

Scal nachodzące na siebie przedziały domknięte `[start, end]` o końcach int,
gdzie `start <= end`. Każdy wiersz wejścia ma dwa elementy. Punkt styku oznacza
nakładanie: `[1,3]` i `[3,5]` dają `[1,5]`. Puste wejście daje pusty wynik.
Nie zmieniamy wejścia, a wynik nie współdzieli jego wierszy.

## Od brute force do niezmiennika

Wyszukiwanie par do kolejnego scalenia powtarza porównania i wymaga wielu
przebiegów. Naiwny wariant skanujący od nowa O(n²) par po każdym z najwyżej
n - 1 scaleń ma górne ograniczenie O(n³). Posortowanie po początku pozwala
rozpatrywać przedziały jeden raz.

**Niezmiennik:** wynik zawiera rozłączne, uporządkowane przedziały pokrywające
już przetworzone wejście. Nowy przedział może połączyć się tylko z ostatnim
wynikiem: wcześniejsze kończą się przed jego początkiem. Jeśli nowy początek
przekracza ostatni koniec, żaden późniejszy przedział nie zamknie tej luki.

## Przykład krok po kroku

Wejście `[[8,10], [1,3], [2,6], [6,7]]` po sortowaniu zaczyna się od `[1,3]`.
`[2,6]` rozszerza koniec do 6, `[6,7]` do 7, a `[8,10]` rozpoczyna nowy wynik.
Otrzymujemy `[[1,7], [8,10]]`. Wiersz zawarty w innym nie skraca jego końca.

## Kod Java

Kontekst: składowa klasy, `java.util.*`. Klonujemy wszystkie wiersze przed sortowaniem.

```java
static int[][] mergeIntervals(int[][] intervals) {
    int[][] sorted = Arrays.stream(intervals).map(int[]::clone).toArray(int[][]::new);
    Arrays.sort(sorted, Comparator.comparingInt(interval -> interval[0]));
    List<int[]> merged = new ArrayList<>();
    for (int[] current : sorted) {
        if (merged.isEmpty() || current[0] > merged.get(merged.size() - 1)[1]) {
            merged.add(current);
        } else {
            int[] last = merged.get(merged.size() - 1);
            last[1] = Math.max(last[1], current[1]);
        }
    }
    return merged.toArray(int[][]::new);
}
```

## Koszt i granice techniki

Czas O(n log n), przejście po sortowaniu O(n). Pamięć pomocnicza O(n), łącznie
z kopiami i buforem sortowania referencji; wynik O(n) w najgorszym przypadku.
Dla gwarantowanego uporządkowanego wejścia sortowanie można pominąć.
Przedziały półotwarte `[start,end)` zmieniają regułę punktu styku.

## Kontrprzykład i scenariusze

Bez sortowania `[5,7], [1,2], [2,6]` może pozostawić rozdzielone fragmenty,
choć ostatecznie wszystkie należą do `[1,7]`. Płytkie klonowanie tylko zewnętrznej
tablicy powoduje modyfikację wierszy wejściowych przy rozszerzaniu końca.

Kontekst: wykonywane asercje, także izolacja stanu wyniku.

```java
static void verify() {
    int[][] input = {{8, 10}, {1, 3}, {2, 6}, {6, 7}};
    int[][] result = mergeIntervals(input);
    assert Arrays.deepEquals(result, new int[][] {{1, 7}, {8, 10}});
    result[0][1] = 99;
    assert Arrays.deepEquals(input, new int[][] {{8, 10}, {1, 3}, {2, 6}, {6, 7}});
    assert Arrays.deepEquals(mergeIntervals(new int[][] {{1, 9}, {2, 3}}),
            new int[][] {{1, 9}});
    assert Arrays.deepEquals(mergeIntervals(new int[][] {{1, 3}, {3, 5}}),
            new int[][] {{1, 5}});
    assert mergeIntervals(new int[0][]).length == 0;
}
```

## Ćwiczenie

Policz minimalną liczbę sal dla półotwartych spotkań. Dlaczego scalanie wszystkich
nakładających się przedziałów nie zachowuje informacji o maksymalnej współbieżności?
