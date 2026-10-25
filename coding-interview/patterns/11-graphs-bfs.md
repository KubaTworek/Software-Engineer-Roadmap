# 11. Grafy i BFS — najkrótsza ścieżka warstwami

[Katalog](../README.md) · [Topological sort](12-topological-sort.md)

## Problem i kontrakt

W skierowanym grafie nieważonym znajdź minimalną liczbę krawędzi od source do
target. Reprezentacja `int[][] graph` to listy sąsiedztwa wierzchołków `0..V-1`;
każdy wiersz istnieje i zawiera poprawne indeksy. Source i target są poprawne.
Cykle, pętle i powtórzone krawędzie są dozwolone. Brak ścieżki daje -1, a droga
z wierzchołka do niego samego ma długość 0. Nie zmieniamy grafu.

## Od brute force do niezmiennika

Wyliczanie wszystkich prostych ścieżek może dać wykładniczo wiele kandydatów.
DFS znajdujący pierwszą ścieżkę nie gwarantuje najkrótszej. BFS przetwarza
wierzchołki warstwami odległości.

**Niezmiennik:** kolejka zawiera odkryte, nieprzetworzone wierzchołki w niemalejącej
odległości od źródła. Pierwsze odkrycie sąsiada daje najkrótszą ścieżkę, bo wszystkie
krótsze warstwy były obsługiwane wcześniej. Oznaczamy odwiedzenie już przy dodaniu,
więc jeden wierzchołek trafia do kolejki najwyżej raz, również w obecności cykli.

## Przykład krok po kroku

Krawędzie `0→1, 0→2, 1→3, 2→3`. Start kolejki `[0]`, odległość 0. Po wyjęciu
zera kolejka to `[1,2]` z odległością 1. Jedynka dodaje trójkę z odległością 2;
dwójka już jej nie dodaje. Wynik dla celu 3 wynosi 2.

## Kod Java

Kontekst: składowa klasy, `java.util.*`. Wartość -1 łączy rolę braku odległości i visited.

```java
static int distance(int[][] graph, int source, int target) {
    int[] distance = new int[graph.length];
    Arrays.fill(distance, -1);
    Deque<Integer> queue = new ArrayDeque<>();
    distance[source] = 0;
    queue.addLast(source);
    while (!queue.isEmpty()) {
        int vertex = queue.removeFirst();
        if (vertex == target) {
            return distance[vertex];
        }
        for (int next : graph[vertex]) {
            if (distance[next] == -1) {
                distance[next] = distance[vertex] + 1;
                queue.addLast(next);
            }
        }
    }
    return -1;
}
```

## Koszt i granice techniki

Czas O(V + E) w najgorszym przypadku, pamięć pomocnicza O(V), wynik O(1).
E to liczba wpisów we wszystkich listach sąsiedztwa. Dla macierzy sąsiedztwa
skanowanie sąsiadów daje O(V²). BFS działa też dla jednakowych dodatnich wag
po przeliczeniu liczby krawędzi na koszt. Różne wagi wymagają innego algorytmu.

## Kontrprzykład i scenariusze

Dla wag `0→1:10`, `0→2:1`, `2→1:1` BFS wybrałby jedną krawędź o koszcie 10,
choć dwie krawędzie kosztują 2. To inny kontrakt niż graf nieważony.

Kontekst: wykonywane asercje, w tym cykl, izolowana składowa i powtórzona krawędź.

```java
static void verify() {
    int[][] graph = {{1, 2}, {3}, {3}, {}, {}};
    assert distance(graph, 0, 3) == 2;
    assert distance(graph, 0, 4) == -1;
    assert distance(graph, 2, 2) == 0;
    assert distance(graph, 3, 0) == -1;
    int[][] cyclic = {{1, 1}, {0, 2}, {2}};
    assert distance(cyclic, 0, 2) == 2;
}
```

## Ćwiczenie

Dodaj tablicę parent i odtwórz jedną najkrótszą ścieżkę. Następnie uruchom BFS
z wielu źródeł jednocześnie: jakie wartości powinny mieć ich odległości na starcie?
