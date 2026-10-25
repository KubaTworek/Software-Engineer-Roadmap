# Kontrola testów — 12-topological-sort

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja TOPO-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:TOPO-01 -->
```java
static boolean canFinish(int courses, int[][] prerequisites) {
    List<List<Integer>> graph = new ArrayList<>(); for (int i = 0; i < courses; i++) { graph.add(new ArrayList<>()); }
    int[] indegree = new int[courses];
    for (int[] pair : prerequisites) { graph.get(pair[1]).add(pair[0]); indegree[pair[0]]++; }
    Deque<Integer> ready = new ArrayDeque<>(); for (int i = 0; i < courses; i++) { if (indegree[i] == 0) { ready.add(i); } }
    int used = 0;
    while (!ready.isEmpty()) { int vertex = ready.removeFirst(); used++; for (int next : graph.get(vertex)) { if (--indegree[next] == 0) { ready.addLast(next); } } }
    return used == courses;
}
```

### Mutacja czesciowy-plan

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:TOPO-01:czesciowy-plan -->
```java
static boolean canFinish(int courses, int[][] prerequisites) {
    List<List<Integer>> graph = new ArrayList<>(); for (int i = 0; i < courses; i++) { graph.add(new ArrayList<>()); }
    int[] indegree = new int[courses];
    for (int[] pair : prerequisites) { graph.get(pair[1]).add(pair[0]); indegree[pair[0]]++; }
    Deque<Integer> ready = new ArrayDeque<>(); for (int i = 0; i < courses; i++) { if (indegree[i] == 0) { ready.add(i); } }
    int used = 0;
    while (!ready.isEmpty()) { int vertex = ready.removeFirst(); used++; for (int next : graph.get(vertex)) { if (--indegree[next] == 0) { ready.addLast(next); } } }
    return used > 0;
}
```

## Referencja TOPO-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:TOPO-02 -->
```java
static int[] smallestOrder(int[][] graph) {
    int[] indegree = new int[graph.length];
    for (int[] neighbors : graph) { for (int next : neighbors) { indegree[next]++; } }
    PriorityQueue<Integer> ready = new PriorityQueue<>();
    for (int i = 0; i < graph.length; i++) { if (indegree[i] == 0) { ready.add(i); } }
    int[] result = new int[graph.length]; int used = 0;
    while (!ready.isEmpty()) { int v = ready.poll(); result[used++] = v; for (int next : graph[v]) { if (--indegree[next] == 0) { ready.add(next); } } }
    return used == graph.length ? result : new int[0];
}
```

## Referencja TOPO-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:TOPO-03 -->
```java
static List<List<Integer>> semesterLayers(int[][] graph) {
    int[] indegree = new int[graph.length];
    for (int[] neighbors : graph) { for (int next : neighbors) { indegree[next]++; } }
    List<Integer> ready = new ArrayList<>(); for (int i = 0; i < graph.length; i++) { if (indegree[i] == 0) { ready.add(i); } }
    List<List<Integer>> result = new ArrayList<>(); int used = 0;
    while (!ready.isEmpty()) {
        Collections.sort(ready); result.add(new ArrayList<>(ready)); used += ready.size(); List<Integer> nextLayer = new ArrayList<>();
        for (int vertex : ready) { for (int next : graph[vertex]) { if (--indegree[next] == 0) { nextLayer.add(next); } } }
        ready = nextLayer;
    }
    return used == graph.length ? result : new ArrayList<>();
}
```
