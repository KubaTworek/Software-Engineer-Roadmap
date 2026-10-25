# Trening mieszany

Najpierw nazwij technikę i uzasadnij wybór. Źródło z kategorią jest ukryte pod każdym zadaniem.

To samodzielny zestaw do edycji: uzupełniaj metody w tym pliku. Z katalogu coding-interview:

```powershell
pwsh -File scripts/check-exercises.ps1 -PracticeFile 'practice/04-session.md' -Exercise P01
```

Ziarno doboru: 45. Importy java.util.* zapewnia skrypt. Testy nie stanowią dowodu złożoności.

## P01 — odtwórz jedną najkrótszą ścieżkę

Dla list sąsiedztwa wiersz graph[u] zawiera docelowe wierzchołki krawędzi z u.
Indeksy są poprawne, wiersze nie-null. Cykle, pętle i duplikaty krawędzi są
dozwolone. Nie zmieniaj grafów ani siatek. V i E do 100 000.

**Poziom:** L2

**Treść i kontrakt.** W niepustym skierowanym grafie nieważonym zwróć tablicę
wierzchołków jednej najkrótszej ścieżki od source do target, wliczając oba końce.
Oba indeksy są poprawne. Brak drogi daje pustą tablicę; source = target daje
jednoelementową. Wybór między równie krótkimi drogami jest dowolny.
Docelowo O(V + E) czasu i O(V) pamięci plus wynik długości O(V).

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P01 -->
```java
static int[] shortestPath(int[][] graph, int source, int target) {
    throw new UnsupportedOperationException("TODO P01");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P01 -->
```java
static void verifyPath(int[][] graph, int source, int target, int[] path, int edges) {
    assert path != null;
    if (edges == -1) { assert path.length == 0; return; }
    assert path.length == edges + 1;
    assert path[0] == source && path[path.length - 1] == target;
    for (int vertex : path) { assert vertex >= 0 && vertex < graph.length; }
    for (int index = 1; index < path.length; index++) {
        boolean found = false;
        for (int next : graph[path[index - 1]]) {
            if (next == path[index]) { found = true; break; }
        }
        assert found;
    }
}

static void verify() {
    int[][] graph = {{1, 2}, {3}, {3}, {0}, {}};
    verifyPath(graph, 0, 3, shortestPath(graph, 0, 3), 2);
    verifyPath(graph, 0, 4, shortestPath(graph, 0, 4), -1);
    verifyPath(graph, 2, 2, shortestPath(graph, 2, 2), 0);
    verifyPath(graph, 3, 2, shortestPath(graph, 3, 2), 2);
    assert Arrays.deepEquals(graph, new int[][] {{1, 2}, {3}, {3}, {0}, {}});
    int[][] direct = {{1, 3}, {2}, {3}, {}};
    verifyPath(direct, 0, 3, shortestPath(direct, 0, 3), 1);
    verifyPath(new int[][] {{}}, 0, 0, shortestPath(new int[][] {{}}, 0, 0), 0);

    int[][] deep = new int[100_000][];
    for (int i = 0; i < deep.length; i++) { deep[i] = i + 1 < deep.length ? new int[] {i + 1} : new int[0]; }
    verifyPath(deep, 0, deep.length - 1, shortestPath(deep, 0, deep.length - 1), deep.length - 1);
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:BFS-03 -->
<!-- snapshot:contract=56c953a5385e465012a760db245115131710536268883098f5cc9f41f2425bdf;tests=ba4eeaaa6064721ef07c2e5a6aecd4ab6e02facd07a98b87fb12030449e6053e;stub=75053f0f14ce9a2fe0152e967819ea8e71d0f7754f98caa910b797ada9824f77;rendered=9661582d5bbcecb2f5d570eb378749ac072e99ef4ca3a5f7e17d65c422a9ce4b -->
[Oryginał BFS-03](../exercises/11-graphs-bfs.md). Otwórz po własnej analizie.

</details>

## P02 — sprawdź ścisłe BST

Drzewo jest acykliczne bez współdzielonych poddrzew, do 10 000 węzłów; głęboki łańcuch dozwolony. Nie zmieniaj wejścia. Null oznacza puste drzewo.

**Poziom:** L1

**Treść i kontrakt.** Sprawdź, czy każde lewe poddrzewo ma wartości ściśle mniejsze od przodka, a prawe ściśle większe. Wartości int, duplikaty zabronione, puste drzewo poprawne. O(n) czasu i O(h) pamięci.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P02 -->
```java
static final class Node {
    final int value; Node left, right;
    Node(int value) { this.value=value; }
}
static boolean isStrictBst(Node root) {
    throw new UnsupportedOperationException("TODO P02");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P02 -->
```java
static void verify() {
    assert isStrictBst(null);
    Node r=new Node(10); r.left=new Node(5); r.right=new Node(15); assert isStrictBst(r);
    r.right.left=new Node(6); assert !isStrictBst(r);
    r.right.left=new Node(10); assert !isStrictBst(r);
    Node x=new Node(Integer.MIN_VALUE); x.right=new Node(Integer.MAX_VALUE); assert isStrictBst(x);

    Node deep = new Node(0), tail = deep;
    for (int i = 1; i < 10_000; i++) { tail.right = new Node(i); tail = tail.right; }
    assert isStrictBst(deep);
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:BST-01 -->
<!-- snapshot:contract=46c6eeb19c6871c92bc2024a0f13f2632f31410941e6c50bdfd381000620d459;tests=13a82d25628d44f6f12e93b265cfb67d4e53ea2c25e254871c13e06618623df1;stub=997d2ca4015d0f6bd64ddc0b6c741c15df21906284ecafdfa6ae474861d908c6;rendered=711c4effa3af2561b3e72361cbac3800e9f6ae1e7ff57e325193036b7db67d7c -->
[Oryginał BST-01](../exercises/23-bst.md). Otwórz po własnej analizie.

</details>

## P03 — ograniczona różnica w fragmencie

Każde zadanie jest niezależne; metody pozostają TODO. Importy java.util.* zapewnia skrypt.

**Poziom:** L2

**Treść i kontrakt.** Zwróć najdłuższy spójny fragment, w którym maksimum minus minimum nie przekracza nieujemnego limit typu long. Wartości int, n w 0..100 000, puste wejście daje 0. Bez mutacji. O(n) czasu i O(n) pamięci.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P03 -->
```java
static int longestLimitedDifference(int[] values, long limit) {
    throw new UnsupportedOperationException("TODO P03");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P03 -->
```java
static void verify() {
    assert longestLimitedDifference(new int[] {8,2,4,7}, 4) == 2;
    assert longestLimitedDifference(new int[] {10,1,2,4,7,2}, 5) == 4;
    assert longestLimitedDifference(new int[] {2,2,2}, 0) == 3;
    assert longestLimitedDifference(new int[0], 0) == 0;
    assert longestLimitedDifference(new int[] {Integer.MIN_VALUE,Integer.MAX_VALUE}, 4294967295L) == 2;
    assert longestLimitedDifference(new int[] {Integer.MIN_VALUE,Integer.MAX_VALUE}, 4294967294L) == 1;
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:DEQUE-02 -->
<!-- snapshot:contract=08a375a43301e4727a76344b41562a2eb9eef9189f7a1c0d3f9c6115ab025c64;tests=336d77b876fcf4e172992ac0db37ea9169d14ad8916944bbff27a5baf146fd75;stub=c7ca1ca26fb5c7c4e0c21c2ae517c280f9bdcc127a330c75ed79549f45d7bb10;rendered=66b2a7229cf55678ab79f8ca050b4ec169076781630662545f683d21d4bb5d1f -->
[Oryginał DEQUE-02](../exercises/19-monotonic-deque.md). Otwórz po własnej analizie.

</details>

## P04 — jeden opcjonalny rabat

Każde zadanie jest niezależne; metody pozostają TODO. Importy java.util.* zapewnia skrypt.

**Poziom:** L3

**Treść i kontrakt.** Graf skierowany opisują [u,v,w] z nieujemnymi wagami int. Od source do target wolno co najwyżej raz zapłacić floor(w/2) zamiast w za jedną krawędź. Zwróć minimalny koszt long lub -1. Source=target daje 0. n≥1,n,E≤100 000, bez mutacji. Docelowo O(n + E log(E+2)) czasu i O(n+E) pamięci.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P04 -->
```java
static long discountedPath(int n, int[][] edges, int source, int target) {
    throw new UnsupportedOperationException("TODO P04");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P04 -->
```java
static void verify() {
    assert discountedPath(3, new int[][] {{0,1,8},{1,2,8},{0,2,25}}, 0, 2) == 12;
    assert discountedPath(2, new int[][] {{0,1,5}}, 0, 1) == 2;
    assert discountedPath(2, new int[0][], 0, 1) == -1;
    assert discountedPath(1, new int[0][], 0, 0) == 0;
    assert discountedPath(2, new int[][] {{0,1,0}}, 0, 1) == 0;
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:DIJK-03 -->
<!-- snapshot:contract=717eebcf91d9dc7a59ed52d93000b69ef03da47af3cbdcf84e7105c31f9e3947;tests=17dfccb815e2c209fbbe906ac554b6a764768e340f7aa63161ed23f54ff5d2c4;stub=c191c732492239116abae03886359e0935cee79f7292668d3192ac8ac1b76554;rendered=4b100421624d6338edd6100de2452ddaeeb02ccfe890d6b4b4c467876d9ac4bb -->
[Oryginał DIJK-03](../exercises/20-dijkstra.md). Otwórz po własnej analizie.

</details>

## P05 — odległość edycyjna

Przed implementacją zapisz znaczenie stanu, bazę, przejście i kolejność obliczeń.
W samych szkieletach nie ma przejść ani podpowiedzi w postaci gotowych stanów.

**Poziom:** L3

**Treść i kontrakt.** Dla dwóch napisów ASCII zwróć minimalną liczbę operacji,
aby przekształcić pierwszy w drugi. Operacja wstawia jeden znak, usuwa jeden znak
albo zamienia jeden znak na inny; każda kosztuje 1. Zamiana kolejności sąsiadów
nie jest pojedynczą operacją. Wielkość liter ma znaczenie. Długości 0..1000.
`kitten` → `sitting` kosztuje 3. Docelowo O((n + 1)(m + 1)) czasu; pamięć
O((n + 1)(m + 1)), opcjonalnie zredukuj ją do liniowej.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P05 -->
```java
static int editDistance(String first, String second) {
    throw new UnsupportedOperationException("TODO P05");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P05 -->
```java
static void verify() {
    assert editDistance("kitten", "sitting") == 3;
    assert editDistance("horse", "ros") == 3;
    assert editDistance("", "abc") == 3;
    assert editDistance("abc", "") == 3;
    assert editDistance("", "") == 0;
    assert editDistance("same", "same") == 0;
    assert editDistance("a", "A") == 1;
    assert editDistance("ab", "ba") == 2;
    assert editDistance("abc", "yabd") == 2;
    assert editDistance("sitting", "kitten") == 3;
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:DP-03 -->
<!-- snapshot:contract=9ff5ffed7f8490b2c378f0eb11146e2fd2f5809243764ccd91b73b90ae198a3c;tests=2ca29c5c6b72d45a0a57cacb009578341d24189618c8f3d6a430456f0c337b1e;stub=e283fc0eddb32cc636aadb140866ae6b44d80209155dad71a343a914a7ca2e53;rendered=8eb9d82e1986d7ca70909d8744327e911e881787f21c6dd7fa3dc3279b424bdb -->
[Oryginał DP-03](../exercises/14-dynamic-programming.md). Otwórz po własnej analizie.

</details>

## P06 — minimalna liczba punktów trafiających przedziały

Przed kodem zapisz regułę wyboru i argument, dlaczego istnieje optimum zgodne
z tym wyborem. Same przykłady testowe nie dowodzą zachłanności.

**Poziom:** L2

**Treść i kontrakt.** Każdy balon jest domkniętym przedziałem int `[start,end]`,
`start <= end`. Strzała w punkcie x trafia wszystkie balony zawierające x.
Zwróć minimalną liczbę strzał potrzebnych do trafienia wszystkich balonów.
Strzały mogą mieć dowolną rzeczywistą współrzędną; zwracasz tylko ich liczbę.
n w 0..100 000, bez mutacji wejścia. `[[1,2],[2,3]]` → 1, bo wspólny punkt 2
należy do obu. Docelowo O(n log n) czasu i O(n) pamięci z zachowaniem wejścia.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P06 -->
```java
static int minArrows(int[][] balloons) {
    throw new UnsupportedOperationException("TODO P06");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P06 -->
```java
static void verify() {
    int[][] input = {{10, 16}, {2, 8}, {1, 6}, {7, 12}};
    assert minArrows(input) == 2;
    assert Arrays.deepEquals(input, new int[][] {{10, 16}, {2, 8}, {1, 6}, {7, 12}});
    assert minArrows(new int[][] {{1, 2}, {2, 3}}) == 1;
    assert minArrows(new int[][] {{1, 2}, {2, 3}, {3, 4}}) == 2;
    assert minArrows(new int[][] {{1, 9}, {2, 3}, {4, 5}}) == 2;
    assert minArrows(new int[][] {{7, 7}, {7, 7}}) == 1;
    assert minArrows(new int[0][]) == 0;
    assert minArrows(new int[][] {{Integer.MIN_VALUE, -1}, {0, Integer.MAX_VALUE}}) == 2;
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:GREEDY-03 -->
<!-- snapshot:contract=4085941dc02b057dd58098cfe9c5db25c8f6a6bdc317906a7c5ccba47218986e;tests=d2cfb5c49c97339f891266c025133125d944cd654ffebb7c5d2043f7f5c9993d;stub=1af1593f8da9864c787b6634757b4654a86fceecb71ee55920e4103f6c38a2b9;rendered=33abe3c1b76359c9da43ad03af8c5d7fae867caf1b88b152b8cf882affad127c -->
[Oryginał GREEDY-03](../exercises/15-greedy.md). Otwórz po własnej analizie.

</details>
