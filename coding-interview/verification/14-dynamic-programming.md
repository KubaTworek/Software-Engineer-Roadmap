# Kontrola testów — 14-dynamic-programming

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja DP-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:DP-01 -->
```java
static long maxNonAdjacentSum(int[] values) {
    long older = 0, newer = 0;
    for (int value : values) { long next = Math.max(newer,older+value); older = newer; newer = next; }
    return newer;
}
```

### Mutacja sasiednie-indeksy

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:DP-01:sasiednie-indeksy -->
```java
static long maxNonAdjacentSum(int[] values) {
    long older = 0, newer = 0;
    for (int value : values) { long next = Math.max(newer,newer+value); older = newer; newer = next; }
    return newer;
}
```

## Referencja DP-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:DP-02 -->
```java
static long countGridPaths(int rows, int columns) {
    if (rows == 0 || columns == 0) { return 0; }
    long[] ways = new long[columns]; ways[0] = 1;
    for (int row = 0; row < rows; row++) { for (int column = 1; column < columns; column++) { ways[column] += ways[column-1]; } }
    return ways[columns-1];
}
```

## Referencja DP-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:DP-03 -->
```java
static int editDistance(String first, String second) {
    int[] previous = new int[second.length()+1]; for (int j = 0; j < previous.length; j++) { previous[j] = j; }
    for (int i = 1; i <= first.length(); i++) {
        int[] current = new int[second.length()+1]; current[0] = i;
        for (int j = 1; j <= second.length(); j++) {
            current[j] = first.charAt(i-1) == second.charAt(j-1) ? previous[j-1]
                : 1+Math.min(previous[j-1],Math.min(previous[j],current[j-1]));
        }
        previous = current;
    }
    return previous[second.length()];
}
```
