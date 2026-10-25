# Kontrola testów — 19-monotonic-deque

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja DEQUE-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:DEQUE-01 -->
```java
static int[] slidingMinimum(int[] values, int k) {
    Deque<Integer> queue = new ArrayDeque<>(); int[] result = new int[values.length-k+1];
    for (int i = 0; i < values.length; i++) {
        while (!queue.isEmpty() && queue.peekFirst() <= i-k) { queue.removeFirst(); }
        while (!queue.isEmpty() && values[queue.peekLast()] >= values[i]) { queue.removeLast(); }
        queue.addLast(i); if (i >= k-1) { result[i-k+1] = values[queue.peekFirst()]; }
    }
    return result;
}
```

### Mutacja spoznione-wygaszenie

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:DEQUE-01:spoznione-wygaszenie -->
```java
static int[] slidingMinimum(int[] values, int k) {
    Deque<Integer> queue = new ArrayDeque<>(); int[] result = new int[values.length-k+1];
    for (int i = 0; i < values.length; i++) {
        while (!queue.isEmpty() && queue.peekFirst() < i-k) { queue.removeFirst(); }
        while (!queue.isEmpty() && values[queue.peekLast()] >= values[i]) { queue.removeLast(); }
        queue.addLast(i); if (i >= k-1) { result[i-k+1] = values[queue.peekFirst()]; }
    }
    return result;
}
```

## Referencja DEQUE-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:DEQUE-02 -->
```java
static int longestLimitedDifference(int[] values, long limit) {
    Deque<Integer> minimum = new ArrayDeque<>(), maximum = new ArrayDeque<>(); int left = 0, best = 0;
    for (int right = 0; right < values.length; right++) {
        while (!minimum.isEmpty() && values[minimum.peekLast()] >= values[right]) { minimum.removeLast(); }
        while (!maximum.isEmpty() && values[maximum.peekLast()] <= values[right]) { maximum.removeLast(); }
        minimum.addLast(right); maximum.addLast(right);
        while ((long) values[maximum.peekFirst()] - values[minimum.peekFirst()] > limit) {
            if (minimum.peekFirst() == left) { minimum.removeFirst(); }
            if (maximum.peekFirst() == left) { maximum.removeFirst(); }
            left++;
        }
        best = Math.max(best,right-left+1);
    }
    return best;
}
```

## Referencja DEQUE-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:DEQUE-03 -->
```java
static int shortestAtLeast(int[] values, long target) {
    long[] prefix = new long[values.length+1]; for (int i = 0; i < values.length; i++) { prefix[i+1] = prefix[i]+values[i]; }
    Deque<Integer> queue = new ArrayDeque<>(); int best = Integer.MAX_VALUE;
    for (int i = 0; i < prefix.length; i++) {
        while (!queue.isEmpty() && prefix[i]-prefix[queue.peekFirst()] >= target) { best = Math.min(best,i-queue.removeFirst()); }
        while (!queue.isEmpty() && prefix[queue.peekLast()] >= prefix[i]) { queue.removeLast(); }
        queue.addLast(i);
    }
    return best == Integer.MAX_VALUE ? -1 : best;
}
```
