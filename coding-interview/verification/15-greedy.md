# Kontrola testów — 15-greedy

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja GREEDY-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:GREEDY-01 -->
```java
static int minRemovals(int[][] meetings) {
    int[][] sorted = meetings.clone(); Arrays.sort(sorted,Comparator.comparingInt(a -> a[1]));
    long end = Long.MIN_VALUE; int kept = 0;
    for (int[] meeting : sorted) { if (meeting[0] >= end) { kept++; end = meeting[1]; } }
    return meetings.length-kept;
}
```

## Referencja GREEDY-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:GREEDY-02 -->
```java
static boolean canReachEnd(int[] jumps) {
    if (jumps.length == 0) { return false; }
    long reach = 0;
    for (int i = 0; i < jumps.length && i <= reach; i++) {
        reach = Math.max(reach,(long) i+jumps[i]); if (reach >= jumps.length-1) { return true; }
    }
    return false;
}
```

## Referencja GREEDY-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:GREEDY-03 -->
```java
static int minArrows(int[][] balloons) {
    int[][] sorted = balloons.clone(); Arrays.sort(sorted,Comparator.comparingInt(a -> a[1]));
    long position = Long.MIN_VALUE; int count = 0;
    for (int[] balloon : sorted) { if (balloon[0] > position) { count++; position = balloon[1]; } }
    return count;
}
```

### Mutacja domkniety-styk

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:GREEDY-03:domkniety-styk -->
```java
static int minArrows(int[][] balloons) {
    int[][] sorted = balloons.clone(); Arrays.sort(sorted,Comparator.comparingInt(a -> a[1]));
    long position = Long.MIN_VALUE; int count = 0;
    for (int[] balloon : sorted) { if (balloon[0] >= position) { count++; position = balloon[1]; } }
    return count;
}
```
