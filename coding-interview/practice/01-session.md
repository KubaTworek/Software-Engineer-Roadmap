# Trening mieszany

Najpierw nazwij technikę i uzasadnij wybór. Źródło z kategorią jest ukryte pod każdym zadaniem.

To samodzielny zestaw do edycji: uzupełniaj metody w tym pliku. Z katalogu coding-interview:

```powershell
pwsh -File scripts/check-exercises.ps1 -PracticeFile 'practice/01-session.md' -Exercise P01
```

Ziarno doboru: 42. Importy java.util.* zapewnia skrypt. Testy nie stanowią dowodu złożoności.

## P01 — całkowity pierwiastek bez funkcji pierwiastka

**Poziom:** L1

**Treść i kontrakt.** Dla `0 <= value <= Integer.MAX_VALUE` zwróć największą
nieujemną liczbę int r, dla której `r² <= value`. Nie używaj sqrt, pow ani obliczeń
zmiennoprzecinkowych. 8 → 2, 9 → 3. Docelowo O(log(value + 2)) czasu i O(1) pamięci.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P01 -->
```java
static int integerSqrt(int value) {
    throw new UnsupportedOperationException("TODO P01");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P01 -->
```java
static void verify() {
    assert integerSqrt(0) == 0;
    assert integerSqrt(1) == 1;
    assert integerSqrt(8) == 2;
    assert integerSqrt(9) == 3;
    assert integerSqrt(Integer.MAX_VALUE) == 46340;
    for (int value : new int[] {2, 3, 15, 16, 17, 1000, 2147395600}) {
        int root = integerSqrt(value);
        assert root >= 0;
        assert (long) root * root <= value;
        assert ((long) root + 1) * ((long) root + 1) > value;
    }
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:BIN-02 -->
<!-- snapshot:contract=eddcaaf71f9cad5f4071ea5df5dd12d5f8a05e9af103717d9f35d907e6eec161;tests=626b95a998cfe94426ffe9142a96fe38567b7231f62350e959dfcb6632b5cf19;stub=ef0baa981ec999e16f114617235f2de4c7763c7d5b6526619fcbc87d706b7229;rendered=a7450750caef264515adbed532f9d3c598644896fbc46eb4fbdfe6225fcab143 -->
[Oryginał BIN-02](../exercises/05-binary-search.md). Otwórz po własnej analizie.

</details>

## P02 — przejście inorder

W każdym zadaniu wejście jest drzewem binarnym: bez cykli i współdzielonych
poddrzew. Pusty korzeń to null. Nie zmieniaj węzłów ani połączeń. Limit n to
10 000 węzłów; głęboki łańcuch jest dopuszczalny. Testy obejmują łańcuch
10 000 węzłów; rozwiązanie ma obsłużyć go bez przepełnienia stosu Javy.

**Poziom:** L1

**Treść i kontrakt.** Zwróć listę wartości z przejścia lewy potomek → węzeł →
prawy potomek. To nie musi być BST, więc wynik nie musi być posortowany.
Zachowaj duplikaty wartości. Pusty korzeń daje pustą listę. Docelowo O(n) czasu
i O(h) pamięci pomocniczej poza wynikiem O(n), gdzie h jest wysokością drzewa.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P02 -->
```java
static final class Node {
    final int value;
    Node left, right;
    Node(int value) { this.value = value; }
}

static List<Integer> inorder(Node root) {
    throw new UnsupportedOperationException("TODO P02");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P02 -->
```java
static void verify() {
    assert inorder(null).isEmpty();
    assert inorder(new Node(7)).equals(List.of(7));
    Node root = new Node(5), left = new Node(9), right = new Node(1), inner = new Node(5);
    root.left = left; root.right = right; left.right = inner;
    assert inorder(root).equals(List.of(9, 5, 5, 1));
    assert root.left == left && root.right == right && left.right == inner;
    assert left.left == null && inner.left == null && inner.right == null;
    assert right.left == null && right.right == null;
    Node chain = new Node(1);
    chain.right = new Node(2); chain.right.right = new Node(3);
    assert inorder(chain).equals(List.of(1, 2, 3));

    Node deep = new Node(0), tail = deep;
    for (int i = 1; i < 10_000; i++) { tail.right = new Node(i); tail = tail.right; }
    List<Integer> traversal = inorder(deep);
    assert traversal.size() == 10_000;
    for (int i = 0; i < traversal.size(); i++) { assert traversal.get(i) == i; }
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:TREE-01 -->
<!-- snapshot:contract=fc6b7eda98c694804662d949f171ef93b7d0174f3e249b7980589c978e613869;tests=7073c600739b4f73487dbe422fa1c5b2892f840b28569b84017006f3c500ae11;stub=df4502eedb78cb9bb5ddaf631afbb698b3bb71d6163cb717a883882fd245f7cc;rendered=ac528f8bdea5fb8527d79ee44c1b453ee7bfdc62bc93f3faec365cd8d89a741a -->
[Oryginał TREE-01](../exercises/10-trees.md). Otwórz po własnej analizie.

</details>

## P03 — najkrótszy fragment dodatnich liczb

**Poziom:** L2

**Treść i kontrakt.** Dla tablicy ściśle dodatnich int i dodatniego target typu
long zwróć minimalną długość spójnego fragmentu o sumie co najmniej target.
Długość 0..100 000. Brak rozwiązania, także puste wejście, daje 0. Nie zmieniaj
tablicy. `[2,3,1,2,4,3]`, target 7 → 2. Docelowo O(n) czasu i O(1) pamięci.
Nie rozszerzaj kontraktu o liczby ujemne bez ponownego uzasadnienia algorytmu.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P03 -->
```java
static int minPositiveWindow(int[] values, long target) {
    throw new UnsupportedOperationException("TODO P03");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P03 -->
```java
static void verify() {
    assert minPositiveWindow(new int[] {2, 3, 1, 2, 4, 3}, 7) == 2;
    assert minPositiveWindow(new int[] {1, 4, 4}, 4) == 1;
    assert minPositiveWindow(new int[] {1, 1, 1}, 4) == 0;
    assert minPositiveWindow(new int[0], 1) == 0;
    assert minPositiveWindow(new int[] {3}, 3) == 1;
    assert minPositiveWindow(new int[] {1, 2, 3}, 6) == 3;
    assert minPositiveWindow(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 4294967294L) == 2;
    assert minPositiveWindow(new int[] {1, 2}, Long.MAX_VALUE) == 0;
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:WIN-03 -->
<!-- snapshot:contract=47aca60edb21fd559e0f6be4801b87f811b33b61cd500dab49c2ea0b8a3010eb;tests=eea1e8545342763ae481d1d6c193610aa73a4ca7f04d81af911f5b9ba3b179cd;stub=026c1ec9e50edad9d32c276376d0579edfb71bfcf2fdd3d6015db59e4c9b886a;rendered=9588a065e686a04087324e3585142c8c19c32d1b3cbcb4ca843948e13117d15e -->
[Oryginał WIN-03](../exercises/03-sliding-window.md). Otwórz po własnej analizie.

</details>

## P04 — słowo bez powtórnego użycia pola

Siatki są prostokątne, wiersze nie-null. Nie zmieniaj wejścia; zwrócone kopie mają własne wiersze.

**Poziom:** L3

**Treść i kontrakt.** Plansza char zawiera małe litery a–z. Sprawdź, czy napis można odczytać idąc bokami i używając każdego pola najwyżej raz w jednej drodze. Pusty napis daje true także dla pustej planszy. Do 36 pól, długość słowa do 12. Po powrocie wejście bez zmian. Podaj koszt zależny od długości słowa; nie oczekujemy liniowego czasu.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P04 -->
```java
static boolean wordExists(char[][] board, String word) {
    throw new UnsupportedOperationException("TODO P04");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P04 -->
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

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:GRID-03 -->
<!-- snapshot:contract=1be85e8db5674451b89bc7367fd89118c83e48cd4d219ad9cfdb40e36f2cb4cc;tests=2e37fa1d8a8e9f85bc1d505c3cb4c80b271471e8769459f5396c3c9aaffcdfb9;stub=571818299a171d43c26ee81e279173f9267e6e39c75408020cbf3d299a9afd8e;rendered=3327aec970ab3b6e1b6c8ad1374abb9abbf3b63aac1b0a18d1f70d0fb616c148 -->
[Oryginał GRID-03](../exercises/24-grids.md). Otwórz po własnej analizie.

</details>

## P05 — pierwszy niepowtarzający się znak

Wszystkie metody poniżej są nierozwiązane. Bloki „szkielet” uzupełniasz, bloki
„testy” uruchamia skrypt dla wskazanego identyfikatora.

**Poziom:** L1

**Treść i kontrakt.** Dla napisu ASCII zwróć indeks pierwszego znaku występującego
dokładnie raz w całym napisie. Jeśli takiego nie ma, zwróć -1. Spacja i wielkość
liter mają znaczenie. Długość 0..100 000. Przykład: `swiss` → 1 (znak w).
Docelowo O(n) czasu i pamięć zależna od alfabetu.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P05 -->
```java
static int firstUniqueIndex(String text) {
    throw new UnsupportedOperationException("TODO P05");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P05 -->
```java
static void verify() {
    assert firstUniqueIndex("swiss") == 1;
    assert firstUniqueIndex("leetcode") == 0;
    assert firstUniqueIndex("aabbc") == 4;
    assert firstUniqueIndex("aabb") == -1;
    assert firstUniqueIndex("") == -1;
    assert firstUniqueIndex("x") == 0;
    assert firstUniqueIndex("aA") == 0;
    assert firstUniqueIndex(" a ") == 1;
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:HASH-01 -->
<!-- snapshot:contract=722c5c3b11772269022fe4d68c3c8fe61a488c9ba936a3a9866626790277f597;tests=ef6e761bf374b3330c55624d7535a0486d911d59bdfaca3bf200dc490ee70459;stub=dff9745b92fe7adc30090ed194e7945fa4ca41de0cfdb8dea66816cc43493f5c;rendered=6f11575bd61bb5eecb0ce7b8f10b44b4d3d21597073aabcddd48e970a146e88e -->
[Oryginał HASH-01](../exercises/01-hash-map.md). Otwórz po własnej analizie.

</details>

## P06 — składowe po każdej krawędzi

Każde zadanie jest niezależne; metody pozostają TODO. Importy java.util.* zapewnia skrypt.

**Poziom:** L2

**Treść i kontrakt.** Zwróć liczbę składowych po każdym kolejnym dodaniu krawędzi nieskierowanej. Uwzględniaj izolowane wierzchołki. n,E do 100 000, poprawne końce, bez mutacji. Puste krawędzie dają pusty wynik. Docelowo O(n + E·α(n)) czasu i O(n) pamięci poza wynikiem.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P06 -->
```java
static int[] componentsAfterEach(int n, int[][] edges) {
    throw new UnsupportedOperationException("TODO P06");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P06 -->
```java
static void verify() {
    assert Arrays.equals(componentsAfterEach(4, new int[][] {{0,1},{2,3},{1,2},{0,3}}), new int[] {3,2,1,1});
    assert Arrays.equals(componentsAfterEach(1, new int[][] {{0,0}}), new int[] {1});
    assert componentsAfterEach(0, new int[0][]).length == 0;
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:DSU-02 -->
<!-- snapshot:contract=30ae86ee4df2dca687099b716f09309c9b7c1c9c7107409261fbe41a92a0e6c3;tests=119912f1eac28541e5ab04356d9107c9a51ed0b37085e0ae91f64eaefd3e896b;stub=4cfc8d781d5c07c924da2832fb93e541ce2a72c6c54554811763376a2f692311;rendered=7cb90834eb9e50ca5b433aa8b590cc8b35a1088a68dc205b4bdc6bbb2f7ca2a1 -->
[Oryginał DSU-02](../exercises/16-union-find.md). Otwórz po własnej analizie.

</details>
