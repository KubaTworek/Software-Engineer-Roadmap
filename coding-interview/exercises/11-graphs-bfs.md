# Zadania: grafy i BFS

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/11-graphs-bfs.md)

Dla list sąsiedztwa wiersz graph[u] zawiera docelowe wierzchołki krawędzi z u.
Indeksy są poprawne, wiersze nie-null. Cykle, pętle i duplikaty krawędzi są
dozwolone. Nie zmieniaj grafów ani siatek. V i E do 100 000.

## BFS-01 — ilu wierzchołków można dosięgnąć

**Poziom:** L1
**Wymagania:** [Karta techniki](../patterns/11-graphs-bfs.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Liczą się wierzchołki, nie różne sposoby dojścia.

</details>

<details>
<summary>Wskazówka 2</summary>

Oznaczaj visited przy dodaniu do struktury pracy.

</details>

<details>
<summary>Wskazówka 3</summary>

Rozpocznij tylko od źródła; cykle i powtórzone krawędzie nie zwiększają wyniku.

</details>

**Treść i kontrakt.** Zwróć liczbę różnych wierzchołków osiągalnych w skierowanym
grafie ze wskazanego source, wliczając source. Graf jest niepusty, source poprawny.
Nie licz różnych dróg jako osobnych wyników. Docelowo O(V + E) czasu, O(V) pamięci.

Kontekst: nierozwiązany szkielet.

<!-- exercise:BFS-01 -->
```java
static int reachableCount(int[][] graph, int source) {
    throw new UnsupportedOperationException("TODO BFS-01");
}
```

Kontekst: wykonywalne testy cykli, rozgałęzień i osobnych składowych.

<!-- tests:BFS-01 -->
```java
static void verify() {
    int[][] graph = {{1, 2}, {3}, {3}, {0}, {}};
    assert reachableCount(graph, 0) == 4;
    assert reachableCount(graph, 4) == 1;
    assert Arrays.deepEquals(graph, new int[][] {{1, 2}, {3}, {3}, {0}, {}});
    assert reachableCount(new int[][] {{}}, 0) == 1;
    assert reachableCount(new int[][] {{0, 1, 1}, {}}, 0) == 2;
    assert reachableCount(new int[][] {{1}, {}}, 1) == 1;

    int[][] deep = new int[100_000][];
    for (int i = 0; i < deep.length; i++) { deep[i] = i + 1 < deep.length ? new int[] {i + 1} : new int[0]; }
    assert reachableCount(deep, 0) == deep.length;
}
```

## BFS-02 — wyspy bez zmieniania mapy

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/11-graphs-bfs.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Jedna wyspa jest składową połączoną bokami.

</details>

<details>
<summary>Wskazówka 2</summary>

Nowy przegląd zaczynaj tylko od nieodwiedzonego lądu.

</details>

<details>
<summary>Wskazówka 3</summary>

Użyj osobnego visited i nie dodawaj sąsiadów po przekątnej.

</details>

**Treść i kontrakt.** Prostokątna siatka char zawiera wyłącznie '0' (woda) i '1'
(ląd). Wyspa to składowa lądu połączona bokami; przekątne nie łączą pól.
Zwróć liczbę wysp. Możliwe jest 0 wierszy lub 0 kolumn; liczba pól do 100 000.
Nie zatapiaj pól w oryginalnej siatce. Docelowo O(R·C) czasu dla niepustej siatki
i O(R·C) pamięci pomocniczej, stały koszt dla pustej.

Kontekst: nierozwiązany szkielet.

<!-- exercise:BFS-02 -->
```java
static int numberOfIslands(char[][] grid) {
    throw new UnsupportedOperationException("TODO BFS-02");
}
```

Kontekst: wykonywalne testy; helper buduje wejście z napisów, nie szuka wysp.

<!-- tests:BFS-02 -->
```java
static char[][] grid(String... rows) {
    return Arrays.stream(rows).map(String::toCharArray).toArray(char[][]::new);
}

static void verify() {
    char[][] input = grid("11000", "11000", "00100", "00011");
    assert numberOfIslands(input) == 3;
    assert Arrays.deepEquals(input, grid("11000", "11000", "00100", "00011"));
    assert numberOfIslands(grid("10", "01")) == 2;
    assert numberOfIslands(grid("111", "111")) == 1;
    assert numberOfIslands(grid("000")) == 0;
    assert numberOfIslands(grid("1")) == 1;
    assert numberOfIslands(new char[0][]) == 0;
    assert numberOfIslands(new char[][] {{}}) == 0;
}
```

## BFS-03 — odtwórz jedną najkrótszą ścieżkę

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/11-graphs-bfs.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Pierwsze odkrycie BFS daje najmniej krawędzi.

</details>

<details>
<summary>Wskazówka 2</summary>

Zapisuj rodzica przy pierwszym dodaniu wierzchołka.

</details>

<details>
<summary>Wskazówka 3</summary>

Odtwórz drogę od celu i odwróć; source=target wymaga jednego elementu.

</details>

**Treść i kontrakt.** W niepustym skierowanym grafie nieważonym zwróć tablicę
wierzchołków jednej najkrótszej ścieżki od source do target, wliczając oba końce.
Oba indeksy są poprawne. Brak drogi daje pustą tablicę; source = target daje
jednoelementową. Wybór między równie krótkimi drogami jest dowolny.
Docelowo O(V + E) czasu i O(V) pamięci plus wynik długości O(V).

Kontekst: nierozwiązany szkielet.

<!-- exercise:BFS-03 -->
```java
static int[] shortestPath(int[][] graph, int source, int target) {
    throw new UnsupportedOperationException("TODO BFS-03");
}
```

Kontekst: wykonywalne testy. Helper sprawdza długość, końce i istnienie krawędzi;
nie wyznacza rozwiązania ani nie narzuca jednej z równych dróg.

<!-- tests:BFS-03 -->
```java
static void verifyPath(int[][] graph, int source, int target, int[] path, int edges) {
    assert path != null;
    if (edges == -1) { assert path.length == 0; return; }
    assert path.length == edges + 1;
    assert path[0] == source && path[path.length - 1] == target;
    for (int vertex : path) { assert vertex >= 0 && vertex < graph.length; }
    for (int index = 1; index < path.length; index++) {
        boolean found = false;
        for (int next : graph[path[index - 1]]) {
            if (next == path[index]) { found = true; break; }
        }
        assert found;
    }
}

static void verify() {
    int[][] graph = {{1, 2}, {3}, {3}, {0}, {}};
    verifyPath(graph, 0, 3, shortestPath(graph, 0, 3), 2);
    verifyPath(graph, 0, 4, shortestPath(graph, 0, 4), -1);
    verifyPath(graph, 2, 2, shortestPath(graph, 2, 2), 0);
    verifyPath(graph, 3, 2, shortestPath(graph, 3, 2), 2);
    assert Arrays.deepEquals(graph, new int[][] {{1, 2}, {3}, {3}, {0}, {}});
    int[][] direct = {{1, 3}, {2}, {3}, {}};
    verifyPath(direct, 0, 3, shortestPath(direct, 0, 3), 1);
    verifyPath(new int[][] {{}}, 0, 0, shortestPath(new int[][] {{}}, 0, 0), 0);

    int[][] deep = new int[100_000][];
    for (int i = 0; i < deep.length; i++) { deep[i] = i + 1 < deep.length ? new int[] {i + 1} : new int[0]; }
    verifyPath(deep, 0, deep.length - 1, shortestPath(deep, 0, deep.length - 1), deep.length - 1);
}
```
