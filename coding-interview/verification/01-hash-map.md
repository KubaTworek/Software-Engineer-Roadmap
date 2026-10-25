# Kontrola testów — 01-hash-map

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja HASH-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:HASH-01 -->
```java
static int firstUniqueIndex(String text) {
    int[] counts = new int[128];
    for (int i = 0; i < text.length(); i++) { counts[text.charAt(i)]++; }
    for (int i = 0; i < text.length(); i++) { if (counts[text.charAt(i)] == 1) { return i; } }
    return -1;
}
```

### Mutacja brak-unikalnosci

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:HASH-01:brak-unikalnosci -->
```java
static int firstUniqueIndex(String text) {
    int[] counts = new int[128];
    for (int i = 0; i < text.length(); i++) { counts[text.charAt(i)]++; }
    for (int i = 0; i < text.length(); i++) { if (counts[text.charAt(i)] > 0) { return i; } }
    return -1;
}
```

## Referencja HASH-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:HASH-02 -->
```java
static boolean areAnagrams(String first, String second) {
    if (first.length() != second.length()) { return false; }
    int[] counts = new int[26];
    for (int i = 0; i < first.length(); i++) { counts[first.charAt(i)-'a']++; counts[second.charAt(i)-'a']--; }
    for (int count : counts) { if (count != 0) { return false; } }
    return true;
}
```

## Referencja HASH-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:HASH-03 -->
```java
static int longestConsecutive(int[] values) {
    Set<Long> seen = new HashSet<>();
    for (int value : values) { seen.add((long) value); }
    int best = 0;
    for (long value : seen) {
        if (seen.contains(value - 1)) { continue; }
        int length = 0;
        for (long next = value; seen.contains(next); next++) { length++; }
        best = Math.max(best, length);
    }
    return best;
}
```
