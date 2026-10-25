# Kontrola testów — 05-binary-search

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja BIN-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:BIN-01 -->
```java
static int[] equalRange(int[] sorted, int target) {
    int left = 0, right = sorted.length;
    while (left < right) { int mid = left+(right-left)/2; if (sorted[mid] < target) { left = mid+1; } else { right = mid; } }
    int first = left; right = sorted.length;
    while (left < right) { int mid = left+(right-left)/2; if (sorted[mid] <= target) { left = mid+1; } else { right = mid; } }
    return new int[] {first,left};
}
```

### Mutacja zla-gorna-granica

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:BIN-01:zla-gorna-granica -->
```java
static int[] equalRange(int[] sorted, int target) {
    int left = 0, right = sorted.length;
    while (left < right) { int mid = left+(right-left)/2; if (sorted[mid] < target) { left = mid+1; } else { right = mid; } }
    int first = left; right = sorted.length;
    while (left < right) { int mid = left+(right-left)/2; if (sorted[mid] < target) { left = mid+1; } else { right = mid; } }
    return new int[] {first,left};
}
```

## Referencja BIN-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:BIN-02 -->
```java
static int integerSqrt(int value) {
    long left = 0, right = (long) value+1;
    while (left < right) {
        long mid = left+(right-left+1)/2;
        if (mid*mid <= value) { left = mid; } else { right = mid-1; }
    }
    return (int) left;
}
```

## Referencja BIN-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:BIN-03 -->
```java
static long minimumCapacity(int[] weights, int days) {
    if (weights.length == 0) { return 0; }
    long left = 0, right = 0;
    for (int weight : weights) { left = Math.max(left,weight); right += weight; }
    while (left < right) {
        long capacity = left+(right-left)/2, load = 0; int used = 1;
        for (int weight : weights) { if (load+weight > capacity) { used++; load = 0; } load += weight; }
        if (used <= days) { right = capacity; } else { left = capacity+1; }
    }
    return left;
}
```
