# 25. DP krok po kroku — od rekurencji do małego stanu

[Katalog](../README.md) · [Karta DP](14-dynamic-programming.md) · [Zadania](../exercises/14-dynamic-programming.md)

## Problem i kontrakt

Policz sposoby wejścia na n schodów krokami długości 1 lub 2. Kolejność kroków
ma znaczenie. Dla n = 0 istnieje jeden pusty sposób. n w 0..40, wynik long.
Wersję czysto rekurencyjną ograniczamy do n ≤ 20, aby ćwiczenie było krótkie.

## Od brute force do niezmiennika

Ostatni krok ma długość 1 lub 2, więc ways(n)=ways(n-1)+ways(n-2), z bazami 1,1.
Rekurencja powtarza podproblemy: ways(5) i ways(4) oba schodzą do ways(3).
Memoizacja przechowuje raz obliczony stan. Tabulacja ustala kolejność od baz
do większych indeksów. Redukcja pamięci wynika z zależności tylko od dwóch
poprzedników, a nie z samej nazwy DP.

## Przykład krok po kroku

n = 4: tablica stanów to `[1,1,2,3,5]`. Przed obliczeniem i para older,newer
przechowuje ways(i-2),ways(i-1). Najpierw liczymy ich sumę, dopiero potem
przesuwamy obie wartości. Ustawienie older=newer przed sumą gubi jeden stan.

## Kod Java

Kontekst: składowe klasy. Wspólny kontrakt n poza ograniczeniem brute force.

```java
static long stairsRecursive(int n) {
    if (n < 0 || n > 20) { throw new IllegalArgumentException("Brute force: 0..20"); }
    return recurse(n);
}
static long recurse(int n) { return n < 2 ? 1 : recurse(n - 1) + recurse(n - 2); }

static long stairsMemo(int n) {
    validate(n); return memo(n, new long[n + 1]);
}
static long memo(int n, long[] cache) {
    if (n < 2) { return 1; }
    if (cache[n] == 0) { cache[n] = memo(n - 1, cache) + memo(n - 2, cache); }
    return cache[n];
}
static long stairsTable(int n) {
    validate(n); long[] dp = new long[n + 2]; dp[0] = 1; dp[1] = 1;
    for (int i = 2; i <= n; i++) { dp[i] = dp[i - 1] + dp[i - 2]; }
    return dp[n];
}
static long stairsSmall(int n) {
    validate(n); long older = 1, newer = 1;
    for (int i = 2; i <= n; i++) { long next = older + newer; older = newer; newer = next; }
    return newer;
}
static void validate(int n) { if (n < 0 || n > 40) { throw new IllegalArgumentException("n: 0..40"); } }
```

## Koszt i granice techniki

Rekurencja ma wykładniczy czas (O(2ⁿ) jako górne ograniczenie) i O(n) stosu.
Memoizacja i tabulacja mają O(n) czasu oraz pamięci; memoizacja dodatkowo O(n)
stos rekursji. Ostatnia wersja ma O(n) czasu i O(1) pamięci. Gdy trzeba odtworzyć
wybraną drogę albo zależności sięgają wielu stanów, pamięci nie można tak samo ograniczyć.

## Kontrprzykład i scenariusze

Ustawienie ways(0)=0 zgubi pustą kontynuację i wynik n=2. W memoizacji zero
może być sentinelą tylko dlatego, że wszystkie poprawne wyniki są dodatnie.

Kontekst: wykonywane porównania czterech wersji i niezależne wartości bazowe.

```java
static void verify() {
    assert stairsSmall(0) == 1 && stairsSmall(1) == 1 && stairsSmall(4) == 5;
    for (int n = 0; n <= 18; n++) {
        long expected = stairsRecursive(n);
        assert stairsMemo(n) == expected && stairsTable(n) == expected && stairsSmall(n) == expected;
    }
    assert stairsSmall(40) == 165580141L;
    assert stairsMemo(40) == stairsTable(40) && stairsTable(40) == stairsSmall(40);
}
```

## Ćwiczenie

Przeprowadź tę samą ewolucję dla DP-01. Pokaż, które gałęzie rekurencji mają
ten sam przyszły problem i dlaczego dwa ostatnie stany wystarczają.
