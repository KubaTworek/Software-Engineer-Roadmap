# 12. Topological sort — przetwarzaj gotowe zależności

[Katalog](../README.md) · [Grafy i BFS](11-graphs-bfs.md)

## Problem i kontrakt

Graf skierowany opisuje zależności: krawędź `u→v` oznacza „u musi być przed v”.
Zwróć dowolny porządek wszystkich wierzchołków zgodny z krawędziami albo pustą
tablicę, gdy istnieje cykl. Pusty graf ma poprawny pusty porządek. Listy sąsiedztwa
zawierają poprawne indeksy; duplikaty krawędzi są liczone i zdejmowane osobno.
Nie zmieniamy wejścia. W cyklicznym grafie nie zwracamy częściowego harmonogramu.

## Od brute force do niezmiennika

Sprawdzenie wszystkich V! permutacji jest zbędne. Wierzchołek bez pozostałych
poprzedników można wykonać od razu. Algorytm Kahna utrzymuje ich liczby — indegree.

**Niezmiennik:** indegree opisuje tylko krawędzie od jeszcze nieprzetworzonych
wierzchołków. Kolejka zawiera wierzchołki o indegree zero. Wyjęcie takiego wierzchołka
jest bezpieczne, a usunięcie jego krawędzi może odblokować następne. Jeśli kolejka
opróżni się przed obsługą V wierzchołków, pozostała część zawiera cykl: podążanie
po pozostałych poprzednikach w skończonym grafie musi powtórzyć wierzchołek.

## Przykład krok po kroku

`0→2, 1→2, 2→3`: indegree wynosi `[0,0,2,1]`. Po obsłudze 0 dwójka nadal czeka
na jednego poprzednika. Obsługa 1 odblokowuje 2, a obsługa 2 odblokowuje 3.
`[0,1,2,3]` i `[1,0,2,3]` są poprawne; porządek nie musi być jednoznaczny.

## Kod Java

Kontekst: składowa klasy, `java.util.*`. Zakładamy, że liczniki indegree mieszczą się w int.

```java
static int[] topologicalOrder(int[][] graph) {
    int[] indegree = new int[graph.length];
    for (int[] neighbors : graph) {
        for (int next : neighbors) {
            indegree[next]++;
        }
    }
    Deque<Integer> ready = new ArrayDeque<>();
    for (int vertex = 0; vertex < graph.length; vertex++) {
        if (indegree[vertex] == 0) {
            ready.addLast(vertex);
        }
    }
    int[] order = new int[graph.length];
    int used = 0;
    while (!ready.isEmpty()) {
        int vertex = ready.removeFirst();
        order[used++] = vertex;
        for (int next : graph[vertex]) {
            if (--indegree[next] == 0) {
                ready.addLast(next);
            }
        }
    }
    return used == graph.length ? order : new int[0];
}
```

## Koszt i granice techniki

Czas O(V + E), pamięć pomocnicza O(V), wynik O(V) dla DAG. Algorytm nie minimalizuje
czasu wykonania zadań z różnymi długościami ani ograniczoną liczbą wykonawców.
Wymaganie najmniejszego leksykograficznie porządku zmienia kolejkę gotowych na kopiec.

## Kontrprzykład i scenariusze

Samo oznaczenie visited jak w BFS nie wykrywa zależności cyklicznych. Również
jedna składowa DAG nie dowodzi, że cały graf jest DAG: osobna składowa może mieć cykl.

Kontekst: wykonywane asercje sprawdzają własności porządku, nie jedną permutację.

```java
static void verify() {
    int[][] graph = {{2}, {2}, {3}, {}, {}};
    int[] order = topologicalOrder(graph);
    assert order.length == graph.length;
    int[] position = new int[graph.length];
    boolean[] seen = new boolean[graph.length];
    for (int i = 0; i < order.length; i++) {
        assert order[i] >= 0 && order[i] < graph.length;
        assert !seen[order[i]];
        seen[order[i]] = true;
        position[order[i]] = i;
    }
    for (int vertex = 0; vertex < graph.length; vertex++) {
        for (int next : graph[vertex]) { assert position[vertex] < position[next]; }
    }
    assert topologicalOrder(new int[][] {{1}, {0}}).length == 0;
    assert topologicalOrder(new int[][] {{}, {2}, {1}}).length == 0;
    assert topologicalOrder(new int[][] {{0}}).length == 0;
    assert Arrays.equals(topologicalOrder(new int[][] {{1, 1}, {}}), new int[] {0, 1});
    assert topologicalOrder(new int[0][]).length == 0;
}
```

## Ćwiczenie

Oblicz najwcześniejsze zakończenia zadań w DAG przy nieograniczonej liczbie
wykonawców. Dlaczego następnik czeka na maksimum zakończeń poprzedników?
