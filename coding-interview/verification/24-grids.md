# Kontrola testów — 24-grids

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja GRID-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:GRID-01 -->
```java
static int[][] floodFillCopy(int[][] colors, int startRow, int startColumn, int newColor) {
    int rows = colors.length, columns = colors[0].length, old = colors[startRow][startColumn];
    int[][] result = Arrays.stream(colors).map(int[]::clone).toArray(int[][]::new);
    if (old == newColor) { return result; }
    Deque<int[]> queue = new ArrayDeque<>(); queue.add(new int[] {startRow,startColumn}); result[startRow][startColumn] = newColor;
    int[][] directions = {{1,0},{-1,0},{0,1},{0,-1}};
    while (!queue.isEmpty()) {
        int[] cell = queue.removeFirst();
        for (int[] direction : directions) {
            int r = cell[0]+direction[0], c = cell[1]+direction[1];
            if (r >= 0 && r < rows && c >= 0 && c < columns && result[r][c] == old) { result[r][c] = newColor; queue.addLast(new int[] {r,c}); }
        }
    }
    return result;
}
```

## Referencja GRID-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:GRID-02 -->
```java
static int shortestBinaryPath(int[][] grid) {
    if (grid.length == 0 || grid[0].length == 0) { return -1; }
    int rows = grid.length, columns = grid[0].length;
    if (grid[0][0] != 0 || grid[rows-1][columns-1] != 0) { return -1; }
    int[][] distance = new int[rows][columns]; distance[0][0] = 1;
    Deque<int[]> queue = new ArrayDeque<>(); queue.add(new int[] {0,0});
    while (!queue.isEmpty()) {
        int[] cell = queue.removeFirst();
        if (cell[0] == rows-1 && cell[1] == columns-1) { return distance[cell[0]][cell[1]]; }
        for (int dr = -1; dr <= 1; dr++) { for (int dc = -1; dc <= 1; dc++) {
            if (dr == 0 && dc == 0) { continue; }
            int r = cell[0]+dr, c = cell[1]+dc;
            if (r >= 0 && r < rows && c >= 0 && c < columns && grid[r][c] == 0 && distance[r][c] == 0) {
                distance[r][c] = distance[cell[0]][cell[1]]+1; queue.addLast(new int[] {r,c});
            }
        } }
    }
    return -1;
}
```

### Mutacja ruchy-zamiast-pol

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:GRID-02:ruchy-zamiast-pol -->
```java
static int shortestBinaryPath(int[][] grid) {
    if (grid.length == 0 || grid[0].length == 0) { return -1; }
    int rows = grid.length, columns = grid[0].length;
    if (grid[0][0] != 0 || grid[rows-1][columns-1] != 0) { return -1; }
    int[][] distance = new int[rows][columns]; distance[0][0] = 0;
    Deque<int[]> queue = new ArrayDeque<>(); queue.add(new int[] {0,0});
    while (!queue.isEmpty()) {
        int[] cell = queue.removeFirst();
        if (cell[0] == rows-1 && cell[1] == columns-1) { return distance[cell[0]][cell[1]]; }
        for (int dr = -1; dr <= 1; dr++) { for (int dc = -1; dc <= 1; dc++) {
            if (dr == 0 && dc == 0) { continue; }
            int r = cell[0]+dr, c = cell[1]+dc;
            if (r >= 0 && r < rows && c >= 0 && c < columns && grid[r][c] == 0 && distance[r][c] == 0) {
                distance[r][c] = distance[cell[0]][cell[1]]+1; queue.addLast(new int[] {r,c});
            }
        } }
    }
    return -1;
}
```

## Referencja GRID-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:GRID-03 -->
```java
static boolean wordExists(char[][] board, String word) {
    if (word.isEmpty()) { return true; }
    if (board.length == 0 || board[0].length == 0) { return false; }
    int rows = board.length, columns = board[0].length; boolean[][] used = new boolean[rows][columns];
    class Search {
        boolean run(int row, int column, int index) {
            if (row < 0 || row >= rows || column < 0 || column >= columns || used[row][column] || board[row][column] != word.charAt(index)) { return false; }
            if (index+1 == word.length()) { return true; }
            used[row][column] = true;
            boolean found = run(row+1,column,index+1) || run(row-1,column,index+1) || run(row,column+1,index+1) || run(row,column-1,index+1);
            used[row][column] = false; return found;
        }
    }
    Search search = new Search();
    for (int row = 0; row < rows; row++) { for (int column = 0; column < columns; column++) { if (search.run(row,column,0)) { return true; } } }
    return false;
}
```
