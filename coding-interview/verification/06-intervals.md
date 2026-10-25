# Kontrola testów — 06-intervals

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja INT-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:INT-01 -->
```java
static int[][] insertInterval(int[][] intervals, int[] added) {
    List<int[]> result = new ArrayList<>(); int index = 0, start = added[0], end = added[1];
    while (index < intervals.length && intervals[index][1] < start) { result.add(intervals[index++].clone()); }
    while (index < intervals.length && intervals[index][0] <= end) {
        start = Math.min(start,intervals[index][0]); end = Math.max(end,intervals[index][1]); index++;
    }
    result.add(new int[] {start,end});
    while (index < intervals.length) { result.add(intervals[index++].clone()); }
    return result.toArray(int[][]::new);
}
```

## Referencja INT-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:INT-02 -->
```java
static int[][] intersectIntervals(int[][] first, int[][] second) {
    List<int[]> result = new ArrayList<>(); int i = 0, j = 0;
    while (i < first.length && j < second.length) {
        int start = Math.max(first[i][0],second[j][0]), end = Math.min(first[i][1],second[j][1]);
        if (start <= end) { result.add(new int[] {start,end}); }
        if (first[i][1] < second[j][1]) { i++; } else { j++; }
    }
    return result.toArray(int[][]::new);
}
```

## Referencja INT-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:INT-03 -->
```java
static int minRooms(int[][] meetings) {
    int[][] sorted = meetings.clone(); Arrays.sort(sorted,Comparator.comparingInt(a -> a[0]));
    PriorityQueue<Integer> ends = new PriorityQueue<>(); int best = 0;
    for (int[] meeting : sorted) {
        while (!ends.isEmpty() && ends.peek() <= meeting[0]) { ends.poll(); }
        ends.add(meeting[1]); best = Math.max(best,ends.size());
    }
    return best;
}
```

### Mutacja zly-punkt-styku

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:INT-03:zly-punkt-styku -->
```java
static int minRooms(int[][] meetings) {
    int[][] sorted = meetings.clone(); Arrays.sort(sorted,Comparator.comparingInt(a -> a[0]));
    PriorityQueue<Integer> ends = new PriorityQueue<>(); int best = 0;
    for (int[] meeting : sorted) {
        while (!ends.isEmpty() && ends.peek() < meeting[0]) { ends.poll(); }
        ends.add(meeting[1]); best = Math.max(best,ends.size());
    }
    return best;
}
```
