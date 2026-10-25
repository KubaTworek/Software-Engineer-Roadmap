# 24. Siatki — współrzędne jako wierzchołki

[Katalog](../README.md) · [Zadania](../exercises/24-grids.md)

## Problem i kontrakt

Prostokątna siatka char zawiera '.' (przejście) i '#' (ściana). Znajdź minimalną
liczbę ruchów bokiem od (0,0) do (R-1,C-1). Brak drogi, zablokowany koniec albo
pusty wymiar daje -1. Jedno otwarte pole daje 0. Nie zmieniamy wejścia.

## Od brute force do niezmiennika

Próbowanie wszystkich ścieżek powtarza odwiedzone pola. Siatka jest grafem
niejawnym z maksymalnie czterema sąsiadami. **Niezmiennik:** BFS przetwarza pola
w warstwach liczby ruchów; pole jest oznaczane przy dodaniu, nie przy wyjęciu.
Granice współrzędnych sprawdzamy przed odczytem komórki.

## Przykład krok po kroku

W siatce `.. / #.` warstwa 0 to (0,0), warstwa 1 to (0,1), warstwa 2 to (1,1).
Wynik 2. Ruch po przekątnej nie jest dozwolony, mimo że wizualnie skróciłby drogę.

## Kod Java

Kontekst: składowa klasy, java.util.*. Każdy wiersz ma ten sam rozmiar.

```java
static int mazeDistance(char[][] grid) {
    int rows = grid.length;
    if (rows == 0 || grid[0].length == 0) { return -1; }
    int columns = grid[0].length;
    if (grid[0][0] == '#' || grid[rows - 1][columns - 1] == '#') { return -1; }
    int[][] distance = new int[rows][columns];
    for (int[] row : distance) { Arrays.fill(row, -1); }
    Deque<int[]> queue = new ArrayDeque<>();
    queue.addLast(new int[] {0, 0}); distance[0][0] = 0;
    int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    while (!queue.isEmpty()) {
        int[] cell = queue.removeFirst();
        for (int[] direction : directions) {
            int row = cell[0] + direction[0], column = cell[1] + direction[1];
            if (row >= 0 && row < rows && column >= 0 && column < columns
                    && grid[row][column] == '.' && distance[row][column] == -1) {
                // Oznaczamy przed dodaniem, aby inni sąsiedzi nie dodali pola drugi raz.
                distance[row][column] = distance[cell[0]][cell[1]] + 1;
                queue.addLast(new int[] {row, column});
            }
        }
    }
    return distance[rows - 1][columns - 1];
}
```

## Koszt i granice techniki

Czas i pamięć O(R·C), wynik O(1). Pusty wymiar ma stały koszt.
Osiągalność może użyć DFS, najkrótsza liczba ruchów uzasadnia BFS. W wyszukiwaniu
słowa nie wolno globalnie oznaczać visited na zawsze: stan użytych pól zależy od gałęzi.

## Kontrprzykład i scenariusze

Zamiana R z C może przejść testy kwadratowej planszy i zawieść na prostokącie.
Sprawdzenie visited dopiero po wyjęciu dopuszcza wielokrotne dodawanie tego samego pola.

Kontekst: wykonywane asercje.

```java
static void verify() {
    assert mazeDistance(new char[][] {{'.', '.'}, {'#', '.'}}) == 2;
    assert mazeDistance(new char[][] {{'.'}}) == 0;
    assert mazeDistance(new char[][] {{'#'}}) == -1;
    assert mazeDistance(new char[0][]) == -1;
    char[][] corridor = new char[1][30_000]; Arrays.fill(corridor[0], '.');
    assert mazeDistance(corridor) == 29_999;
}
```

## Ćwiczenie

Odtwórz drogę jako współrzędne. Następnie zmień zestaw dozwolonych kierunków i testy.
