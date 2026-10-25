# Zadania: operacje bitowe

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/18-bit-operations.md)

Każde zadanie jest niezależne; metody pozostają TODO. Importy java.util.* zapewnia skrypt.

## BIT-01 — liczba ustawionych bitów

**Poziom:** L1
**Wymagania:** [operacje bitowe](../patterns/18-bit-operations.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Policz jedynki w 32-bitowym zapisie int, także dla liczb ujemnych. Nie używaj Integer.bitCount ani zamiany na napis. Wynik 0..32, stały koszt względem szerokości int i O(1) pamięci.

<details>
<summary>Wskazówka 1</summary>

Analizuj wzorzec bitów, nie znak matematycznej liczby.

</details>

<details>
<summary>Wskazówka 2</summary>

Przesunięcie arytmetyczne ujemnej liczby może nigdy nie dojść do zera.

</details>

<details>
<summary>Wskazówka 3</summary>

Możesz użyć >>> albo usuwać najniższy ustawiony bit; określ miarę postępu.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:BIT-01 -->
```java
static int countBits(int value) {
    throw new UnsupportedOperationException("TODO BIT-01");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:BIT-01 -->
```java
static void verify() {
    assert countBits(0) == 0; assert countBits(7) == 3;
    assert countBits(-1) == 32; assert countBits(Integer.MIN_VALUE) == 1;
    assert countBits(Integer.MAX_VALUE) == 31; assert countBits(0x55555555) == 16;
}
```

## BIT-02 — brakująca liczba

**Poziom:** L1
**Wymagania:** [operacje bitowe](../patterns/18-bit-operations.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Tablica długości n zawiera różne wartości z 0..n, dokładnie jednej brakuje. Zwróć ją, bez mutacji wejścia. n w 0..100 000. Docelowo O(n) czasu, O(1) pamięci; uzasadnij brak przepełnienia.

<details>
<summary>Wskazówka 1</summary>

Pełny zbiór 0..n ma o jeden element więcej niż wejście.

</details>

<details>
<summary>Wskazówka 2</summary>

Połącz agregat pełnego zbioru z agregatem danych.

</details>

<details>
<summary>Wskazówka 3</summary>

W XOR każda obecna wartość wystąpi dwa razy; pamiętaj o końcowym n.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:BIT-02 -->
```java
static int missingNumber(int[] values) {
    throw new UnsupportedOperationException("TODO BIT-02");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:BIT-02 -->
```java
static void verify() {
    assert missingNumber(new int[] {3,0,1}) == 2;
    assert missingNumber(new int[0]) == 0;
    assert missingNumber(new int[] {0}) == 1;
    assert missingNumber(new int[] {1}) == 0;
    assert missingNumber(new int[] {0,1,2}) == 3;
}
```

## BIT-03 — dwie pojedyncze wartości

**Poziom:** L2
**Wymagania:** [operacje bitowe](../patterns/18-bit-operations.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** W niepustej tablicy dokładnie dwie różne wartości int występują raz, wszystkie pozostałe dwa razy. Zwróć dwie pojedyncze wartości rosnąco. n w 2..100 000, bez mutacji, O(n) czasu i O(1) pamięci.

<details>
<summary>Wskazówka 1</summary>

XOR wszystkich danych to XOR dwóch poszukiwanych wartości, więc nie jest zerem.

</details>

<details>
<summary>Wskazówka 2</summary>

Jeden ustawiony bit tego wyniku rozdziela obie wartości do różnych grup.

</details>

<details>
<summary>Wskazówka 3</summary>

Redukuj XOR osobno w obu grupach; maska może być bitem znaku.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:BIT-03 -->
```java
static int[] twoSingles(int[] values) {
    throw new UnsupportedOperationException("TODO BIT-03");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:BIT-03 -->
```java
static void verify() {
    assert Arrays.equals(twoSingles(new int[] {1,2,1,3,2,5}), new int[] {3,5});
    assert Arrays.equals(twoSingles(new int[] {0,-1}), new int[] {-1,0});
    assert Arrays.equals(twoSingles(new int[] {Integer.MIN_VALUE,0,7,7}), new int[] {Integer.MIN_VALUE,0});
    assert Arrays.equals(twoSingles(new int[] {-4,-2,3,3}), new int[] {-4,-2});
}
```
