# Kontrola testów — 02-two-pointers

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja TWO-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:TWO-01 -->
```java
static int[] uniqueSorted(int[] sorted) {
    int size = 0;
    for (int i = 0; i < sorted.length; i++) { if (i == 0 || sorted[i] != sorted[i-1]) { size++; } }
    int[] result = new int[size]; int index = 0;
    for (int i = 0; i < sorted.length; i++) { if (i == 0 || sorted[i] != sorted[i-1]) { result[index++] = sorted[i]; } }
    return result;
}
```

## Referencja TWO-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:TWO-02 -->
```java
static long[] sortedSquares(int[] sorted) {
    long[] result = new long[sorted.length]; int left = 0, right = sorted.length-1;
    for (int index = result.length-1; index >= 0; index--) {
        long a = (long) sorted[left] * sorted[left], b = (long) sorted[right] * sorted[right];
        if (a > b) { result[index] = a; left++; } else { result[index] = b; right--; }
    }
    return result;
}
```

### Mutacja mnozenie-int

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:TWO-02:mnozenie-int -->
```java
static long[] sortedSquares(int[] sorted) {
    long[] result = new long[sorted.length]; int left = 0, right = sorted.length-1;
    for (int index = result.length-1; index >= 0; index--) {
        long a = sorted[left] * sorted[left], b = (long) sorted[right] * sorted[right];
        if (a > b) { result[index] = a; left++; } else { result[index] = b; right--; }
    }
    return result;
}
```

## Referencja TWO-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:TWO-03 -->
```java
static long countPairs(int[] sorted, int target) {
    int left = 0, right = sorted.length-1; long count = 0;
    while (left < right) {
        long sum = (long) sorted[left] + sorted[right];
        if (sum < target) { left++; } else if (sum > target) { right--; }
        else if (sorted[left] == sorted[right]) {
            long size = right-left+1L; count += size * (size-1) / 2; break;
        } else {
            int a = sorted[left], b = sorted[right]; long first = 0, second = 0;
            while (left <= right && sorted[left] == a) { first++; left++; }
            while (right >= left && sorted[right] == b) { second++; right--; }
            count += first * second;
        }
    }
    return count;
}
```
