# 20. Dijkstra — najmniejszy znany dystans

[Katalog](../README.md) · [Zadania](../exercises/20-dijkstra.md)

## Problem i kontrakt

Graf skierowany ma nieujemne wagi int. graph[u] jest tablicą par `[v,weight]`.
Source jest poprawny, V ≥ 1, indeksy poprawne, wiersze nie-null. Odległości long;
nieosiągalne wierzchołki mają -1. Pętle i wielokrotne krawędzie są dozwolone.
Wejścia nie zmieniamy. Ograniczenia V,E ≤ 100 000 gwarantują brak przepełnienia long.

## Od brute force do niezmiennika

Enumeracja ścieżek powtarza stany. Można też wybierać najmniejszy dystans liniowo
w O(V² + E); kopiec przyspiesza wybór. **Niezmiennik:** aktualny dystans opisuje
koszt pewnej znalezionej drogi, a wyjęty aktualny najmniejszy dystans jest już
optymalny. Nieujemne krawędzie nie pozwolą późniejszej, droższej drodze go poprawić.

## Przykład krok po kroku

0→1 koszt 10, 0→2 koszt 1, 2→1 koszt 1: po źródle znamy 10 i 1; wyjmujemy 2,
poprawiamy dystans 1 na 2. Stary wpis `(1,10)` zostaje w kopcu, ale pomijamy go
jako nieaktualny. Nie oznaczamy ostatecznego visited przy pierwszym dodaniu.

## Kod Java

Kontekst: składowe klasy, java.util.*. Para State jest niemutowalnym wpisem kopca.

```java
record State(int vertex, long distance) {}

static long[] distances(int[][][] graph, int source) {
    long[] best = new long[graph.length];
    Arrays.fill(best, Long.MAX_VALUE); best[source] = 0;
    PriorityQueue<State> queue = new PriorityQueue<>(Comparator.comparingLong(State::distance));
    queue.add(new State(source, 0));
    while (!queue.isEmpty()) {
        State state = queue.poll();
        // W kopcu może pozostać stary wpis sprzed poprawy dystansu.
        if (state.distance() != best[state.vertex()]) { continue; }
        for (int[] edge : graph[state.vertex()]) {
            long candidate = state.distance() + edge[1];
            if (candidate < best[edge[0]]) {
                best[edge[0]] = candidate;
                queue.add(new State(edge[0], candidate));
            }
        }
    }
    for (int i = 0; i < best.length; i++) { if (best[i] == Long.MAX_VALUE) { best[i] = -1; } }
    return best;
}
```

## Koszt i granice techniki

Wersja z leniwym usuwaniem starych wpisów: O(V + E log(E + 2)) czasu i O(V + E)
pamięci pomocniczej w najgorszym przypadku. Kopiec może mieć O(E), nie O(V), wpisów.
Dla wag równych 1 BFS jest prostszy; dla ujemnych wag powyższe uzasadnienie nie działa.

## Kontrprzykład i scenariusze

Pierwsze dodanie wierzchołka nie zamyka wyniku — przykład 10 kontra 1+1 to obala.
Nie zmieniaj dystansu w obiekcie już znajdującym się w PriorityQueue.

Kontekst: wykonywane asercje.

```java
static void verify() {
    int[][][] graph = {{{1, 10}, {2, 1}}, {}, {{1, 1}}, {}};
    assert Arrays.equals(distances(graph, 0), new long[] {0, 2, 1, -1});
    assert Arrays.equals(distances(new int[][][] {{{1, 0}}, {{0, 0}}}, 0), new long[] {0, 0});
    assert Arrays.equals(distances(new int[][][] {{{1, Integer.MAX_VALUE}}, {{2, Integer.MAX_VALUE}}, {}}, 0),
            new long[] {0, Integer.MAX_VALUE, 4294967294L});
}
```

## Ćwiczenie

Odtwórz ścieżkę przez parent. Nie nadpisuj rodzica przy równym dystansie bez reguły
remisów — zerowe krawędzie mogą wtedy wprowadzić cykl do łańcucha rodziców.
