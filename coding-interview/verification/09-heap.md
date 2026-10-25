# Kontrola testów — 09-heap

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja HEAP-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:HEAP-01 -->
```java
static int[] kLargest(int[] values, int k) {
    if (k == 0) { return new int[0]; }
    PriorityQueue<Integer> heap = new PriorityQueue<>();
    for (int value : values) { heap.add(value); if (heap.size() > k) { heap.poll(); } }
    int[] result = new int[k];
    for (int i = k-1; i >= 0; i--) { result[i] = heap.poll(); }
    return result;
}
```

### Mutacja rosnacy-wynik

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:HEAP-01:rosnacy-wynik -->
```java
static int[] kLargest(int[] values, int k) {
    if (k == 0) { return new int[0]; }
    PriorityQueue<Integer> heap = new PriorityQueue<>();
    for (int value : values) { heap.add(value); if (heap.size() > k) { heap.poll(); } }
    int[] result = new int[k];
    for (int i = k-1; i >= 0; i--) { result[k-1-i] = heap.poll(); }
    return result;
}
```

## Referencja HEAP-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:HEAP-02 -->
```java
static int[][] closestPoints(int[][] points, int k) {
    Comparator<int[]> order = Comparator.<int[]>comparingLong(p -> (long) p[0]*p[0]+(long) p[1]*p[1])
        .thenComparingInt(p -> p[0]).thenComparingInt(p -> p[1]);
    PriorityQueue<int[]> heap = new PriorityQueue<>(order.reversed());
    for (int[] point : points) { heap.add(point); if (heap.size() > k) { heap.poll(); } }
    int[][] result = new int[k][];
    for (int i = k-1; i >= 0; i--) { result[i] = heap.poll().clone(); }
    return result;
}
```

## Referencja HEAP-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:HEAP-03 -->
```java
static int[] mergeSortedArrays(int[][] sources) {
    PriorityQueue<int[]> heap = new PriorityQueue<>(Comparator.comparingInt(a -> a[0])); int size = 0;
    for (int i = 0; i < sources.length; i++) { size += sources[i].length; if (sources[i].length > 0) { heap.add(new int[] {sources[i][0],i,0}); } }
    int[] result = new int[size]; int index = 0;
    while (!heap.isEmpty()) {
        int[] entry = heap.poll(); result[index++] = entry[0]; int next = entry[2]+1;
        if (next < sources[entry[1]].length) { heap.add(new int[] {sources[entry[1]][next],entry[1],next}); }
    }
    return result;
}
```
