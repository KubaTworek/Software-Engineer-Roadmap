# Kontrola testów — 11-graphs-bfs

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja BFS-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:BFS-01 -->
```java
static int reachableCount(int[][] graph, int source) {
    boolean[] seen = new boolean[graph.length]; Deque<Integer> queue = new ArrayDeque<>(); seen[source] = true; queue.add(source); int count = 0;
    while (!queue.isEmpty()) { int vertex = queue.removeFirst(); count++; for (int next : graph[vertex]) { if (!seen[next]) { seen[next] = true; queue.addLast(next); } } }
    return count;
}
```

### Mutacja zrodlo-nieodwiedzone

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:BFS-01:zrodlo-nieodwiedzone -->
```java
static int reachableCount(int[][] graph, int source) {
    boolean[] seen = new boolean[graph.length]; Deque<Integer> queue = new ArrayDeque<>(); /* brak oznaczenia źródła */ queue.add(source); int count = 0;
    while (!queue.isEmpty()) { int vertex = queue.removeFirst(); count++; for (int next : graph[vertex]) { if (!seen[next]) { seen[next] = true; queue.addLast(next); } } }
    return count;
}
```

## Referencja BFS-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:BFS-02 -->
```java
static int numberOfIslands(char[][] grid) {
    if (grid.length == 0 || grid[0].length == 0) { return 0; }
    int rows = grid.length, columns = grid[0].length, count = 0; boolean[][] seen = new boolean[rows][columns];
    int[][] directions = {{1,0},{-1,0},{0,1},{0,-1}};
    for (int row = 0; row < rows; row++) {
        for (int column = 0; column < columns; column++) {
            if (grid[row][column] != '1' || seen[row][column]) { continue; }
            count++; Deque<int[]> queue = new ArrayDeque<>(); queue.add(new int[] {row,column}); seen[row][column] = true;
            while (!queue.isEmpty()) {
                int[] cell = queue.removeFirst();
                for (int[] direction : directions) {
                    int r = cell[0]+direction[0], c = cell[1]+direction[1];
                    if (r >= 0 && r < rows && c >= 0 && c < columns && !seen[r][c] && grid[r][c] == '1') {
                        seen[r][c] = true; queue.addLast(new int[] {r,c});
                    }
                }
            }
        }
    }
    return count;
}
```

## Referencja BFS-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:BFS-03 -->
```java
static int[] shortestPath(int[][] graph, int source, int target) {
    int[] parent = new int[graph.length]; Arrays.fill(parent,-1); parent[source] = source;
    Deque<Integer> queue = new ArrayDeque<>(); queue.add(source);
    while (!queue.isEmpty()) {
        int vertex = queue.removeFirst();
        if (vertex == target) { break; }
        for (int next : graph[vertex]) { if (parent[next] == -1) { parent[next] = vertex; queue.addLast(next); } }
    }
    if (parent[target] == -1) { return new int[0]; }
    int length = 1; for (int v = target; v != source; v = parent[v]) { length++; }
    int[] result = new int[length]; int v = target;
    for (int i = length-1; i >= 0; i--) { result[i] = v; v = parent[v]; }
    return result;
}
```
