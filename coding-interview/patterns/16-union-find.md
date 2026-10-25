# 16. Union-Find — sklejaj składowe

[Katalog](../README.md) · [Zadania](../exercises/16-union-find.md)

## Problem i kontrakt

Policz składowe nieskierowanego grafu o wierzchołkach 0..n-1 po dodaniu wszystkich
krawędzi. n ≥ 0, końce krawędzi są poprawne. Pętle i duplikaty są dozwolone.
Izolowane wierzchołki też są składowymi. Nie zmieniamy wejścia.

## Od brute force do niezmiennika

Po każdym dodaniu można przeszukiwać cały graf, co powtarza pracę. DSU przechowuje
podział na zbiory, każdy reprezentowany przez korzeń drzewa rodziców.
**Niezmiennik:** dwa wierzchołki mają ten sam korzeń dokładnie wtedy, gdy należą do
tej samej składowej po dotychczasowych krawędziach. Unia różnych korzeni zmniejsza
licznik o jeden; unia tego samego korzenia nie zmienia podziału.

## Przykład krok po kroku

n = 5: początkowo 5 składowych. Dodaj 0–1 → 4, 1–2 → 3, 2–0 → nadal 3,
3–4 → 2. Łączenie mniejszego drzewa pod większe ogranicza wysokość.
Kompresja ścieżki skraca następne wyszukiwania, zachowując korzeń zbioru.

## Kod Java

Kontekst: składowe klasy, java.util.*. find jest iteracyjny; size ma znaczenie dla korzeni.

```java
static final class Dsu {
    final int[] parent, size;
    int components;
    Dsu(int n) {
        parent = new int[n]; size = new int[n]; components = n;
        for (int i = 0; i < n; i++) { parent[i] = i; size[i] = 1; }
    }
    int find(int vertex) {
        while (vertex != parent[vertex]) {
            // Skracamy ścieżkę, ale nie zmieniamy reprezentowanego zbioru.
            parent[vertex] = parent[parent[vertex]];
            vertex = parent[vertex];
        }
        return vertex;
    }
    boolean union(int first, int second) {
        int a = find(first), b = find(second);
        if (a == b) { return false; }
        // Rozmiar porównujemy dla korzeni, nie dla pierwotnych końców krawędzi.
        if (size[a] < size[b]) { int swap = a; a = b; b = swap; }
        parent[b] = a; size[a] += size[b]; components--;
        return true;
    }
}

static int componentCount(int n, int[][] edges) {
    Dsu dsu = new Dsu(n);
    for (int[] edge : edges) { dsu.union(edge[0], edge[1]); }
    return dsu.components;
}
```

## Koszt i granice techniki

Inicjalizacja O(n), potem amortyzowane O(α(n)) na operację przy kompresji i unii
według rozmiaru; pamięć O(n), wynik O(1). α jest odwrotnością funkcji Ackermanna,
nie literalną stałą. DSU nie odtwarza ścieżek i nie obsługuje zwykłego usuwania
krawędzi. Dla jednorazowego grafu BFS/DFS również daje O(V + E).

## Kontrprzykład i scenariusze

Zmniejszanie components przy każdej krawędzi błędnie policzy trójkąt jako zero
składowych. Union-Find nie zastępuje wykrywania cyklu skierowanego.

Kontekst: wykonywane asercje.

```java
static void verify() {
    assert componentCount(5, new int[][] {{0, 1}, {1, 2}, {2, 0}, {3, 4}}) == 2;
    assert componentCount(3, new int[][] {{0, 0}, {0, 1}, {0, 1}}) == 2;
    assert componentCount(0, new int[0][]) == 0;
    Dsu dsu = new Dsu(100_000);
    for (int i = 1; i < 100_000; i++) { assert dsu.union(i - 1, i); }
    assert dsu.components == 1 && dsu.find(0) == dsu.find(99_999);
    assert !dsu.union(0, 99_999);
}
```

## Ćwiczenie

Wykryj pierwszą zbędną krawędź. Oddziel informację „unia nic nie zmieniła” od
numeru reprezentanta, który jest szczegółem implementacji.
