# Zadania: siatki

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/24-grids.md)

Siatki są prostokątne, wiersze nie-null. Nie zmieniaj wejścia; zwrócone kopie mają własne wiersze.

## GRID-01 — kolorowanie na kopii

**Poziom:** L1
**Wymagania:** [siatki](../patterns/24-grids.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Dla niepustej siatki int, poprawnego startRow/startColumn i newColor zwróć kopię, na której składowa koloru pola startowego połączona bokami ma nowy kolor. Inne pola bez zmian. Do 100 000 pól. Także przy identycznym nowym kolorze wynik nie może współdzielić wierszy. O(RC) czasu i pamięci.

<details>
<summary>Wskazówka 1</summary>

Składowa jest wyznaczona przez pierwotny kolor startu.

</details>

<details>
<summary>Wskazówka 2</summary>

Użyj przeglądu sąsiadów i oznaczaj odwiedzenie przy dodaniu.

</details>

<details>
<summary>Wskazówka 3</summary>

Zrób kopie wierszy; gdy kolory są równe, nadal potrzebujesz bezpiecznej obsługi odwiedzeń.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:GRID-01 -->
```java
static int[][] floodFillCopy(int[][] colors, int startRow, int startColumn, int newColor) {
    throw new UnsupportedOperationException("TODO GRID-01");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:GRID-01 -->
```java
static void verify() {
    int[][] input={{1,1,0},{1,0,0}};
    int[][] result=floodFillCopy(input,0,0,7);
    assert Arrays.deepEquals(result,new int[][] {{7,7,0},{7,0,0}});
    result[0][0]=9; assert Arrays.deepEquals(input,new int[][] {{1,1,0},{1,0,0}});
    int[][] same=floodFillCopy(input,0,0,1); same[1][0]=99; assert input[1][0]==1;
    assert Arrays.deepEquals(floodFillCopy(new int[][] {{2}},0,0,3),new int[][] {{3}});
}
```

## GRID-02 — najkrótsza droga z przekątnymi

**Poziom:** L2
**Wymagania:** [siatki](../patterns/24-grids.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Siatka int zawiera 0 (wolne) i 1 (blokada). Ruch do jednego z ośmiu sąsiadów, także po przekątnej; wolno przejść po przekątnej między dwiema blokadami. Od (0,0) do prawego dolnego pola zwróć liczbę pól na najkrótszej drodze, wliczając oba końce. Brak drogi, pusty wymiar lub zablokowany koniec daje -1. Do 100 000 pól, O(RC) czasu i pamięci.

<details>
<summary>Wskazówka 1</summary>

Każdy dozwolony ruch kosztuje tyle samo; pole może mieć osiem krawędzi.

</details>

<details>
<summary>Wskazówka 2</summary>

Oznaczaj pole przy dodaniu do kolejki i sprawdzaj osobno granice wiersza oraz kolumny.

</details>

<details>
<summary>Wskazówka 3</summary>

Kontrakt liczy pola, więc odległość startu wynosi 1, nie 0.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:GRID-02 -->
```java
static int shortestBinaryPath(int[][] grid) {
    throw new UnsupportedOperationException("TODO GRID-02");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:GRID-02 -->
```java
static void verify() {
    assert shortestBinaryPath(new int[][] {{0,1},{1,0}})==2;
    assert shortestBinaryPath(new int[][] {{0}})==1;
    assert shortestBinaryPath(new int[][] {{1}})==-1;
    assert shortestBinaryPath(new int[][] {{0,0,0},{1,1,0}})==3;
    assert shortestBinaryPath(new int[0][])==-1;
    assert shortestBinaryPath(new int[][] {{}})==-1;
    assert shortestBinaryPath(new int[][] {{0,1,0}})==-1;
}
```

## GRID-03 — słowo bez powtórnego użycia pola

**Poziom:** L3
**Wymagania:** [siatki](../patterns/24-grids.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Plansza char zawiera małe litery a–z. Sprawdź, czy napis można odczytać idąc bokami i używając każdego pola najwyżej raz w jednej drodze. Pusty napis daje true także dla pustej planszy. Do 36 pól, długość słowa do 12. Po powrocie wejście bez zmian. Podaj koszt zależny od długości słowa; nie oczekujemy liniowego czasu.

<details>
<summary>Wskazówka 1</summary>

Odwiedzone pola zależą od bieżącej drogi, nie globalnie od całego wyszukiwania.

</details>

<details>
<summary>Wskazówka 2</summary>

Stan obejmuje pole, indeks litery i użyte pola; po powrocie cofnij oznaczenie.

</details>

<details>
<summary>Wskazówka 3</summary>

Spróbuj różnych startów i odcinaj gałąź przy niezgodnej literze; odtwórz stan także przy sukcesie.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:GRID-03 -->
```java
static boolean wordExists(char[][] board, String word) {
    throw new UnsupportedOperationException("TODO GRID-03");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:GRID-03 -->
```java
static void verify() {
    char[][] board={{'a','b','c'},{'d','e','f'}};
    assert wordExists(board,"abef"); assert !wordExists(board,"aba");
    assert Arrays.deepEquals(board,new char[][] {{'a','b','c'},{'d','e','f'}});
    assert wordExists(new char[0][],""); assert !wordExists(new char[0][],"a");
    assert wordExists(new char[][] {{'a'}},"a");
    assert !wordExists(new char[][] {{'a'}},"aa");
    assert !wordExists(board,"af");
}
```
