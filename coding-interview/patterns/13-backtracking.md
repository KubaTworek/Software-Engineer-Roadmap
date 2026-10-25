# 13. Backtracking — wybierz, zejdź, cofnij

[Katalog](../README.md) · [Dynamic programming](14-dynamic-programming.md)

## Problem i kontrakt

Wypisz wszystkie podzbiory tablicy różnych wartości int, w tym pusty. Każdy element
może być użyty najwyżej raz. Kolejność podzbiorów nie jest częścią kontraktu;
wewnątrz podzbioru zachowujemy kolejność wejścia. Dla pustego wejścia wynik zawiera
jeden pusty podzbiór. Wyniki mają niezależne listy, wejścia nie zmieniamy.

## Od brute force do niezmiennika

Istnieje 2ⁿ podzbiorów, więc enumeracja jest nieunikniona. Zamiast tworzyć osobne
kopie stanu przy każdej decyzji, backtracking rozwija jedną ścieżkę i cofa zmianę
po powrocie. W problemach z ograniczeniami dodatkowo odcina niedopuszczalne gałęzie.

**Niezmiennik:** przy wejściu do `enumerate` path zawiera rosnący ciąg indeksów
wybranych przed start, a kolejne wybory są dozwolone tylko od start. Każdy podzbiór
ma dokładnie jeden taki ciąg, więc pojawi się raz. Po powrocie z rekurencji usunięcie
ostatniej wartości odtwarza stan dla następnej gałęzi. Start rośnie, więc zejście
ma ograniczoną głębokość.

## Przykład krok po kroku

Dla `[1,2]`: zapisz `[]`, wybierz 1 i zapisz `[1]`, wybierz 2 i zapisz `[1,2]`.
Cofnij 2, cofnij 1, wybierz 2 od korzenia i zapisz `[2]`. Gałąź `[2,1]` nie
powstaje, bo indeks następnego wyboru musi być większy.

## Kod Java

Kontekst: składowe klasy, `java.util.*`. Kopia przy zapisie wyniku jest konieczna.

```java
static List<List<Integer>> subsets(int[] values) {
    List<List<Integer>> result = new ArrayList<>();
    enumerate(values, 0, new ArrayList<>(), result);
    return result;
}

static void enumerate(int[] values, int start, List<Integer> path,
                      List<List<Integer>> result) {
    result.add(new ArrayList<>(path));
    for (int index = start; index < values.length; index++) {
        path.add(values[index]);
        enumerate(values, index + 1, path, result);
        // Następna gałąź musi zobaczyć dokładnie stan sprzed wyboru.
        path.remove(path.size() - 1);
    }
}
```

## Koszt i granice techniki

Dla n ≥ 1 czas Θ(n·2ⁿ) i pamięć wyniku Θ(n·2ⁿ) uwzględniają kopiowanie podzbiorów.
Pamięć pomocnicza O(n) to ścieżka i stos. Puste wejście wymaga stałego kosztu.
To nie jest algorytm wielomianowy tylko dlatego, że każda ścieżka ma długość n.
Dla dużego n trzeba zmienić wymaganie wyniku albo sposób jego dostarczania.

## Kontrprzykład i scenariusze

`result.add(path)` zapisuje wiele referencji do jednego bufora: po cofnięciu
wszystkich decyzji każdy wynik wygląda jak pusta lista. Z kolei `[1,1]` łamie
warunek różnych wartości i wymaga osobnej obsługi duplikatów wyników.

Kontekst: wykonywane asercje, w tym kompletność, unikalność i niezależność list.

```java
static void verify() {
    assert subsets(new int[0]).equals(List.of(List.of()));
    List<List<Integer>> result = subsets(new int[] {1, 2, 3});
    Set<List<Integer>> expected = Set.of(List.of(), List.of(1), List.of(2), List.of(3),
            List.of(1, 2), List.of(1, 3), List.of(2, 3), List.of(1, 2, 3));
    assert result.size() == 8;
    assert new HashSet<>(result).equals(expected);
    List<List<Integer>> separate = subsets(new int[] {1});
    separate.get(0).add(99);
    assert separate.get(1).equals(List.of(1));
}
```

## Ćwiczenie

Wypisz kombinacje k elementów. Odetnij gałąź, jeżeli pozostało za mało kandydatów.
Potem dopuść powtarzające się wartości wejściowe i pomijaj duplikaty na tym samym poziomie.
