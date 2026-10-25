# Kontrola testów — 04-prefix-sum

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja PRE-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:PRE-01 -->
```java
static long[] rangeSums(int[] values, int[][] queries) {
    long[] prefix = new long[values.length+1], result = new long[queries.length];
    for (int i = 0; i < values.length; i++) { prefix[i+1] = prefix[i] + values[i]; }
    for (int i = 0; i < queries.length; i++) { result[i] = prefix[queries[i][1]] - prefix[queries[i][0]]; }
    return result;
}
```

## Referencja PRE-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:PRE-02 -->
```java
static int longestBalancedBinary(int[] values) {
    Map<Integer,Integer> first = new HashMap<>(); first.put(0,-1);
    int balance = 0, best = 0;
    for (int i = 0; i < values.length; i++) {
        balance += values[i] == 0 ? -1 : 1;
        Integer previous = first.putIfAbsent(balance,i);
        if (previous != null) { best = Math.max(best, i-previous); }
    }
    return best;
}
```

## Referencja PRE-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:PRE-03 -->
```java
static long countDivisible(int[] values, int d) {
    long[] frequency = new long[d]; frequency[0] = 1; long result = 0; int remainder = 0;
    for (int value : values) {
        remainder = (int) Math.floorMod((long) remainder + value, (long) d);
        result += frequency[remainder]; frequency[remainder]++;
    }
    return result;
}
```

### Mutacja brak-pustego-prefiksu

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:PRE-03:brak-pustego-prefiksu -->
```java
static long countDivisible(int[] values, int d) {
    long[] frequency = new long[d]; frequency[0] = 0; long result = 0; int remainder = 0;
    for (int value : values) {
        remainder = (int) Math.floorMod((long) remainder + value, (long) d);
        result += frequency[remainder]; frequency[remainder]++;
    }
    return result;
}
```
