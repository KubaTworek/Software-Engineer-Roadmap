# Kontrola testów — 07-monotonic-stack

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja STACK-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:STACK-01 -->
```java
static int[] nextGreaterCircular(int[] values) {
    int n = values.length; int[] result = new int[n]; Arrays.fill(result,-1);
    Deque<Integer> stack = new ArrayDeque<>();
    for (int i = 0; i < 2*n; i++) {
        int index = i%n;
        while (!stack.isEmpty() && values[index] > values[stack.peek()]) { result[stack.pop()] = index; }
        if (i < n) { stack.push(index); }
    }
    return result;
}
```

## Referencja STACK-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:STACK-02 -->
```java
static int[] stockSpans(int[] prices) {
    int[] result = new int[prices.length]; Deque<Integer> stack = new ArrayDeque<>();
    for (int i = 0; i < prices.length; i++) {
        while (!stack.isEmpty() && prices[stack.peek()] <= prices[i]) { stack.pop(); }
        result[i] = stack.isEmpty() ? i+1 : i-stack.peek(); stack.push(i);
    }
    return result;
}
```

### Mutacja rowne-ceny-blokuja

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:STACK-02:rowne-ceny-blokuja -->
```java
static int[] stockSpans(int[] prices) {
    int[] result = new int[prices.length]; Deque<Integer> stack = new ArrayDeque<>();
    for (int i = 0; i < prices.length; i++) {
        while (!stack.isEmpty() && prices[stack.peek()] < prices[i]) { stack.pop(); }
        result[i] = stack.isEmpty() ? i+1 : i-stack.peek(); stack.push(i);
    }
    return result;
}
```

## Referencja STACK-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:STACK-03 -->
```java
static long largestRectangle(int[] heights) {
    long best = 0; Deque<Integer> stack = new ArrayDeque<>();
    for (int i = 0; i <= heights.length; i++) {
        int current = i == heights.length ? -1 : heights[i];
        while (!stack.isEmpty() && heights[stack.peek()] > current) {
            int height = heights[stack.pop()]; int left = stack.isEmpty() ? -1 : stack.peek();
            best = Math.max(best,(long) height * (i-left-1));
        }
        if (i < heights.length) { stack.push(i); }
    }
    return best;
}
```
