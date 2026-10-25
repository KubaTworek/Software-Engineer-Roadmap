# Kontrola testów — 03-sliding-window

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja WIN-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:WIN-01 -->
```java
static long maxFixedSum(int[] values, int k) {
    long sum = 0;
    for (int i = 0; i < k; i++) { sum += values[i]; }
    long best = sum;
    for (int i = k; i < values.length; i++) { sum += (long) values[i] - values[i-k]; best = Math.max(best, sum); }
    return best;
}
```

### Mutacja pusty-wybor

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:WIN-01:pusty-wybor -->
```java
static long maxFixedSum(int[] values, int k) {
    long sum = 0;
    for (int i = 0; i < k; i++) { sum += values[i]; }
    long best = 0;
    for (int i = k; i < values.length; i++) { sum += (long) values[i] - values[i-k]; best = Math.max(best, sum); }
    return best;
}
```

## Referencja WIN-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:WIN-02 -->
```java
static int longestAtMostK(String text, int k) {
    int[] counts = new int[128]; int left = 0, distinct = 0, best = 0;
    for (int right = 0; right < text.length(); right++) {
        if (counts[text.charAt(right)]++ == 0) { distinct++; }
        while (distinct > k) { if (--counts[text.charAt(left++)] == 0) { distinct--; } }
        best = Math.max(best, right-left+1);
    }
    return best;
}
```

## Referencja WIN-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:WIN-03 -->
```java
static int minPositiveWindow(int[] values, long target) {
    int left = 0, best = Integer.MAX_VALUE; long sum = 0;
    for (int right = 0; right < values.length; right++) {
        sum += values[right];
        while (sum >= target) { best = Math.min(best, right-left+1); sum -= values[left++]; }
    }
    return best == Integer.MAX_VALUE ? 0 : best;
}
```
