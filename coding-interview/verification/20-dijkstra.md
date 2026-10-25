# Kontrola testów — 20-dijkstra

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja DIJK-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:DIJK-01 -->
```java
static long networkDelay(int n, int[][] edges, int source) {
    List<List<int[]>> graph = new ArrayList<>(); for (int i = 0; i < n; i++) { graph.add(new ArrayList<>()); }
    for (int[] edge : edges) { graph.get(edge[0]).add(new int[] {edge[1],edge[2]}); }
    long[] best = new long[n]; Arrays.fill(best,Long.MAX_VALUE); best[source] = 0;
    PriorityQueue<long[]> queue = new PriorityQueue<>(Comparator.comparingLong(a -> a[0])); queue.add(new long[] {0,source});
    while (!queue.isEmpty()) {
        long[] state = queue.poll(); int vertex = (int) state[1]; if (state[0] != best[vertex]) { continue; }
        for (int[] edge : graph.get(vertex)) { long candidate = state[0]+edge[1]; if (candidate < best[edge[0]]) { best[edge[0]] = candidate; queue.add(new long[] {candidate,edge[0]}); } }
    }
    long result = 0; for (long distance : best) { if (distance == Long.MAX_VALUE) { return -1; } result = Math.max(result,distance); }
    return result;
}
```

## Referencja DIJK-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:DIJK-02 -->
```java
static long minCostGrid(int[][] costs) {
    int rows = costs.length, columns = costs[0].length; long[] best = new long[rows*columns]; Arrays.fill(best,Long.MAX_VALUE); best[0] = costs[0][0];
    PriorityQueue<long[]> queue = new PriorityQueue<>(Comparator.comparingLong(a -> a[0])); queue.add(new long[] {best[0],0});
    int[][] directions = {{1,0},{-1,0},{0,1},{0,-1}};
    while (!queue.isEmpty()) {
        long[] state = queue.poll(); int vertex = (int) state[1]; if (state[0] != best[vertex]) { continue; }
        int row = vertex/columns, column = vertex%columns;
        for (int[] direction : directions) {
            int r = row+direction[0], c = column+direction[1];
            if (r >= 0 && r < rows && c >= 0 && c < columns) {
                int next = r*columns+c; long candidate = state[0]+costs[r][c];
                if (candidate < best[next]) { best[next] = candidate; queue.add(new long[] {candidate,next}); }
            }
        }
    }
    return best[best.length-1];
}
```

### Mutacja pominiety-start

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:DIJK-02:pominiety-start -->
```java
static long minCostGrid(int[][] costs) {
    int rows = costs.length, columns = costs[0].length; long[] best = new long[rows*columns]; Arrays.fill(best,Long.MAX_VALUE); best[0] = 0;
    PriorityQueue<long[]> queue = new PriorityQueue<>(Comparator.comparingLong(a -> a[0])); queue.add(new long[] {best[0],0});
    int[][] directions = {{1,0},{-1,0},{0,1},{0,-1}};
    while (!queue.isEmpty()) {
        long[] state = queue.poll(); int vertex = (int) state[1]; if (state[0] != best[vertex]) { continue; }
        int row = vertex/columns, column = vertex%columns;
        for (int[] direction : directions) {
            int r = row+direction[0], c = column+direction[1];
            if (r >= 0 && r < rows && c >= 0 && c < columns) {
                int next = r*columns+c; long candidate = state[0]+costs[r][c];
                if (candidate < best[next]) { best[next] = candidate; queue.add(new long[] {candidate,next}); }
            }
        }
    }
    return best[best.length-1];
}
```

## Referencja DIJK-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:DIJK-03 -->
```java
static long discountedPath(int n, int[][] edges, int source, int target) {
    List<List<int[]>> graph = new ArrayList<>(); for (int i = 0; i < n; i++) { graph.add(new ArrayList<>()); }
    for (int[] edge : edges) { graph.get(edge[0]).add(new int[] {edge[1],edge[2]}); }
    long[][] best = new long[n][2]; for (long[] row : best) { Arrays.fill(row,Long.MAX_VALUE); } best[source][0] = 0;
    PriorityQueue<long[]> queue = new PriorityQueue<>(Comparator.comparingLong(a -> a[0])); queue.add(new long[] {0,source,0});
    while (!queue.isEmpty()) {
        long[] state = queue.poll(); int vertex = (int) state[1], used = (int) state[2]; if (state[0] != best[vertex][used]) { continue; }
        for (int[] edge : graph.get(vertex)) {
            for (int nextUsed = used; nextUsed <= 1; nextUsed++) {
                long candidate = state[0] + (nextUsed > used ? edge[1]/2 : edge[1]);
                if (candidate < best[edge[0]][nextUsed]) { best[edge[0]][nextUsed] = candidate; queue.add(new long[] {candidate,edge[0],nextUsed}); }
            }
        }
    }
    long answer = Math.min(best[target][0],best[target][1]); return answer == Long.MAX_VALUE ? -1 : answer;
}
```
