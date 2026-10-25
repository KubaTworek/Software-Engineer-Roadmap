# Kontrola testów — 16-union-find

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja DSU-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:DSU-01 -->
```java
static int redundantEdges(int n, int[][] edges) {
    class Dsu {
        int[] parent = new int[n], size = new int[n]; int count = n;
        Dsu() { for (int i = 0; i < n; i++) { parent[i] = i; size[i] = 1; } }
        int find(int v) { while (v != parent[v]) { parent[v] = parent[parent[v]]; v = parent[v]; } return v; }
        boolean union(int a, int b) {
            a = find(a); b = find(b); if (a == b) { return false; }
            if (size[a] < size[b]) { int swap = a; a = b; b = swap; }
            parent[b] = a; size[a] += size[b]; count--; return true;
        }
    }
    Dsu dsu = new Dsu();
    int redundant = 0;
    for (int[] edge : edges) { if (!dsu.union(edge[0],edge[1])) { redundant++; } }
    return redundant;
}
```

## Referencja DSU-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:DSU-02 -->
```java
static int[] componentsAfterEach(int n, int[][] edges) {
    class Dsu {
        int[] parent = new int[n], size = new int[n]; int count = n;
        Dsu() { for (int i = 0; i < n; i++) { parent[i] = i; size[i] = 1; } }
        int find(int v) { while (v != parent[v]) { parent[v] = parent[parent[v]]; v = parent[v]; } return v; }
        boolean union(int a, int b) {
            a = find(a); b = find(b); if (a == b) { return false; }
            if (size[a] < size[b]) { int swap = a; a = b; b = swap; }
            parent[b] = a; size[a] += size[b]; count--; return true;
        }
    }
    Dsu dsu = new Dsu();
    int[] result = new int[edges.length];
    for (int i = 0; i < edges.length; i++) { dsu.union(edges[i][0],edges[i][1]); result[i] = dsu.count; }
    return result;
}
```

### Mutacja duplikat-zmienia-licznik

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:DSU-02:duplikat-zmienia-licznik -->
```java
static int[] componentsAfterEach(int n, int[][] edges) {
    class Dsu {
        int[] parent = new int[n], size = new int[n]; int count = n;
        Dsu() { for (int i = 0; i < n; i++) { parent[i] = i; size[i] = 1; } }
        int find(int v) { while (v != parent[v]) { parent[v] = parent[parent[v]]; v = parent[v]; } return v; }
        boolean union(int a, int b) {
            a = find(a); b = find(b); if (a == b) { count--; return false; }
            if (size[a] < size[b]) { int swap = a; a = b; b = swap; }
            parent[b] = a; size[a] += size[b]; count--; return true;
        }
    }
    Dsu dsu = new Dsu();
    int[] result = new int[edges.length];
    for (int i = 0; i < edges.length; i++) { dsu.union(edges[i][0],edges[i][1]); result[i] = dsu.count; }
    return result;
}
```

## Referencja DSU-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:DSU-03 -->
```java
static boolean[] connectedQueries(int n, int[][] edges, int[][] queries) {
    class Dsu {
        int[] parent = new int[n], size = new int[n]; int count = n;
        Dsu() { for (int i = 0; i < n; i++) { parent[i] = i; size[i] = 1; } }
        int find(int v) { while (v != parent[v]) { parent[v] = parent[parent[v]]; v = parent[v]; } return v; }
        boolean union(int a, int b) {
            a = find(a); b = find(b); if (a == b) { return false; }
            if (size[a] < size[b]) { int swap = a; a = b; b = swap; }
            parent[b] = a; size[a] += size[b]; count--; return true;
        }
    }
    Dsu dsu = new Dsu();
    for (int[] edge : edges) { dsu.union(edge[0],edge[1]); }
    boolean[] result = new boolean[queries.length];
    for (int i = 0; i < queries.length; i++) { result[i] = dsu.find(queries[i][0]) == dsu.find(queries[i][1]); }
    return result;
}
```
