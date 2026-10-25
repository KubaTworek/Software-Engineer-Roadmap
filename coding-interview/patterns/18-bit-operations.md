# 18. Operacje bitowe — jawny model bitów

[Katalog](../README.md) · [Zadania](../exercises/18-bit-operations.md)

## Problem i kontrakt

W niepustej tablicy int dokładnie jedna wartość występuje raz, każda pozostała
dokładnie dwa razy. Zwróć tę pojedynczą wartość, bez mutacji. Znak liczby nie ma
znaczenia: operujemy na 32 bitach int, również dla MIN_VALUE.

## Od brute force do niezmiennika

Porównanie każdego elementu ze wszystkimi kosztuje O(n²); mapa liczników wymaga
O(n) pamięci. XOR spełnia a XOR a = 0, a XOR 0 = a i jest łączny oraz przemienny.
**Niezmiennik:** akumulator jest XOR-em całego przetworzonego prefiksu. Po końcu
wszystkie pary się redukują, pozostawiając jedyną nieparzystą wartość.

## Przykład krok po kroku

`[4,1,4,2,2]`: akumulator przyjmuje 4, 5, 1, 3, 1. Wynik 1.
Nie traktujemy XOR jako dodawania; przeniesienia bitowe nie występują.

## Kod Java

Kontekst: składowa klasy, bez importów.

```java
static int singleValue(int[] values) {
    int result = 0;
    for (int value : values) { result ^= value; }
    return result;
}
```

## Koszt i granice techniki

Czas O(n), pamięć i wynik O(1). Poprawność zależy od liczności, nie od sortowania.
Jeżeli dwie wartości występują raz, pojedynczy XOR daje ich XOR, nie obie wartości.
W Javie >> rozszerza znak, >>> dopisuje zera. Dla int odległość przesunięcia jest
maskowana do pięciu dolnych bitów: `1 << 32` to 1, a nie zero. Maska 1L dotyczy
long; nie zmieniaj typu po wykonaniu operacji. `1 << 31` ma ujemny znak int.

## Kontrprzykład i scenariusze

`[1,2]` daje XOR 3, choć 3 nie występuje. To wejście narusza kontrakt. Użycie sumy
i odejmowania par wymaga innych informacji i może się przepełnić.

Kontekst: wykonywane asercje.

```java
static void verify() {
    assert singleValue(new int[] {4, 1, 4, 2, 2}) == 1;
    assert singleValue(new int[] {-1, -1, Integer.MIN_VALUE}) == Integer.MIN_VALUE;
    assert singleValue(new int[] {0}) == 0;
    assert (1 << 32) == 1;
    assert (-1 >>> 31) == 1 && (-1 >> 31) == -1;
}
```

## Ćwiczenie

Znajdź dwie wartości występujące raz. Jaki bit pozwala podzielić je na różne grupy?
