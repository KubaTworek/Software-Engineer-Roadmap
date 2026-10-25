# Kontrola testów — 18-bit-operations

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja BIT-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:BIT-01 -->
```java
static int countBits(int value) {
    int count = 0;
    while (value != 0) { value &= value-1; count++; }
    return count;
}
```

### Mutacja pominiete-ujemne

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:BIT-01:pominiete-ujemne -->
```java
static int countBits(int value) {
    int count = 0;
    while (value > 0) { value &= value-1; count++; }
    return count;
}
```

## Referencja BIT-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:BIT-02 -->
```java
static int missingNumber(int[] values) {
    int result = values.length;
    for (int i = 0; i < values.length; i++) { result ^= i ^ values[i]; }
    return result;
}
```

## Referencja BIT-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:BIT-03 -->
```java
static int[] twoSingles(int[] values) {
    int xor = 0; for (int value : values) { xor ^= value; }
    int mask = xor & -xor, first = 0, second = 0;
    for (int value : values) { if ((value & mask) != 0) { first ^= value; } else { second ^= value; } }
    return first < second ? new int[] {first,second} : new int[] {second,first};
}
```
