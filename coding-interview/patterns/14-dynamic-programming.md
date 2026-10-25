# 14. Dynamic programming — policz każdy stan raz

[Katalog](../README.md) · [Greedy](15-greedy.md)

## Problem i kontrakt

Dla dodatnich nominałów `int[] coins` i kwoty `amount` znajdź minimalną liczbę
monet. Każdy nominał można wykorzystać dowolnie wiele razy. Brak rozwiązania
daje -1; kwota 0 wymaga 0 monet, również przy pustej liście nominałów. Duplikaty
nominałów są dozwolone. Wejścia nie zmieniamy. W tej edukacyjnej implementacji
ograniczamy amount do `0..100_000`, aby jawnie ograniczyć tablicę stanów.

## Od brute force do niezmiennika

Rekurencja próbująca każdy nominał dla pozostałej kwoty wielokrotnie rozwiązuje
te same podproblemy. DP zapamiętuje wynik: stan `dp[x]` to minimalna liczba monet
do uzyskania dokładnie x. Nie potrzebujemy kolejności wcześniejszych monet.

**Przejście:** `dp[x] = min(dp[x - coin] + 1)` po nominałach `coin <= x`.
Stan początkowy to `dp[0] = 0`, nieosiągalność oznaczamy przez `amount + 1`.
Każde rozwiązanie ma ostatnią monetę, więc przejście rozważa wszystkie możliwości.
Jej usunięcie daje mniejszy stan, już obliczony w rosnącym porządku kwot.

## Przykład krok po kroku

Nominały `[1,3,4]`, kwota `6`: dp dla 0..6 wynosi `[0,1,2,1,1,2,2]`.
Przy 6 kandydaci to `dp[5]+1 = 3`, `dp[3]+1 = 2`, `dp[2]+1 = 3`.
Wynik 2 odpowiada `3+3`, podczas gdy zachłanny wybór `4+1+1` wymaga trzech monet.

## Kod Java

Kontekst: składowa klasy, `java.util.*`. Wszystkie nominały walidujemy przed DP.

```java
static int minCoins(int[] coins, int amount) {
    if (amount < 0 || amount > 100_000) {
        throw new IllegalArgumentException("Kwota poza zakresem karty");
    }
    for (int coin : coins) {
        if (coin <= 0) {
            throw new IllegalArgumentException("Nominały muszą być dodatnie");
        }
    }
    int unreachable = amount + 1;
    int[] dp = new int[amount + 1];
    Arrays.fill(dp, unreachable);
    dp[0] = 0;
    for (int sum = 1; sum <= amount; sum++) {
        for (int coin : coins) {
            if (coin <= sum && dp[sum - coin] != unreachable) {
                dp[sum] = Math.min(dp[sum], dp[sum - coin] + 1);
            }
        }
    }
    return dp[amount] == unreachable ? -1 : dp[amount];
}
```

## Koszt i granice techniki

Dla A = amount i m nominałów: O((A + 1)·(m + 1)) czasu, w tym walidacja i inicjalizacja;
typowo zapisuje się O(A·m) dla dodatnich A i m. Pamięć O(A + 1), wynik O(1).
To koszt pseudowielomianowy: zależy od wartości kwoty, a nie liczby bitów jej zapisu.
Ograniczona liczba monet wymaga innego stanu lub porządku aktualizacji.

## Kontrprzykład i scenariusze

Ustawienie całego dp na zero błędnie uzna nieosiągalne kwoty za dostępne bez monet.
Nominał zero w rekurencji nie zmniejszałby kwoty. Gdy pytanie dotyczy liczby kombinacji,
operator minimum i powyższy kontrakt wyniku nie wystarczają.

Kontekst: wykonywane asercje dla optimum, nieosiągalności i walidacji.

```java
static void verify() {
    assert minCoins(new int[] {1, 3, 4}, 6) == 2;
    assert minCoins(new int[] {2}, 3) == -1;
    assert minCoins(new int[0], 0) == 0;
    assert minCoins(new int[0], 1) == -1;
    assert minCoins(new int[] {2, 2}, 4) == 2;
    boolean rejected = false;
    try { minCoins(new int[] {0, 1}, 3); }
    catch (IllegalArgumentException expected) { rejected = true; }
    assert rejected;
}
```

## Ćwiczenie

Odtwórz jedną optymalną listę monet, zapisując wybór dla każdego stanu. Następnie
policz kombinacje: wyjaśnij, dlaczego kolejność pętli wpływa na liczenie permutacji.
