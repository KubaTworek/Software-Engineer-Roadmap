# Zadania: Union-Find

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/16-union-find.md)

Każde zadanie jest niezależne; metody pozostają TODO. Importy java.util.* zapewnia skrypt.

## DSU-01 — zbędne krawędzie

**Poziom:** L1
**Wymagania:** [Union-Find](../patterns/16-union-find.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Policz krawędzie, których dodanie nie zmienia składowych nieskierowanego grafu. Wierzchołki 0..n-1, n w 0..100 000, poprawne końce, pętle i duplikaty dozwolone. Nie zmieniaj wejścia. Docelowo O(n + E·α(n)) amortyzowanego czasu, O(n) pamięci.

<details>
<summary>Wskazówka 1</summary>

Czy końce są połączone jeszcze przed dodaniem krawędzi?

</details>

<details>
<summary>Wskazówka 2</summary>

Porównuj reprezentantów składowych, nie bezpośrednich rodziców.

</details>

<details>
<summary>Wskazówka 3</summary>

Udana unia zmienia podział; nieudana zwiększa liczbę zbędnych krawędzi.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:DSU-01 -->
```java
static int redundantEdges(int n, int[][] edges) {
    throw new UnsupportedOperationException("TODO DSU-01");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:DSU-01 -->
```java
static void verify() {
    assert redundantEdges(3, new int[][] {{0,1},{1,2},{2,0}}) == 1;
    assert redundantEdges(2, new int[][] {{0,0},{0,1},{1,0}}) == 2;
    assert redundantEdges(0, new int[0][]) == 0;
    assert redundantEdges(5, new int[0][]) == 0;
}
```

## DSU-02 — składowe po każdej krawędzi

**Poziom:** L2
**Wymagania:** [Union-Find](../patterns/16-union-find.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Zwróć liczbę składowych po każdym kolejnym dodaniu krawędzi nieskierowanej. Uwzględniaj izolowane wierzchołki. n,E do 100 000, poprawne końce, bez mutacji. Puste krawędzie dają pusty wynik. Docelowo O(n + E·α(n)) czasu i O(n) pamięci poza wynikiem.

<details>
<summary>Wskazówka 1</summary>

Jaka liczba składowych istnieje przed pierwszą krawędzią?

</details>

<details>
<summary>Wskazówka 2</summary>

Zmniejszaj licznik tylko przy łączeniu różnych zbiorów.

</details>

<details>
<summary>Wskazówka 3</summary>

Zapisz stan licznika po każdej próbie unii, także nieudanej.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:DSU-02 -->
```java
static int[] componentsAfterEach(int n, int[][] edges) {
    throw new UnsupportedOperationException("TODO DSU-02");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:DSU-02 -->
```java
static void verify() {
    assert Arrays.equals(componentsAfterEach(4, new int[][] {{0,1},{2,3},{1,2},{0,3}}), new int[] {3,2,1,1});
    assert Arrays.equals(componentsAfterEach(1, new int[][] {{0,0}}), new int[] {1});
    assert componentsAfterEach(0, new int[0][]).length == 0;
}
```

## DSU-03 — zapytania o połączenie

**Poziom:** L2
**Wymagania:** [Union-Find](../patterns/16-union-find.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Po dodaniu wszystkich nieskierowanych krawędzi odpowiedz boolean[] na pary [a,b]: czy są w tej samej składowej? n≥1, n,E,q do 100 000, indeksy poprawne. Wierzchołek jest połączony z samym sobą. Bez mutacji. Docelowo O(n + (E+q)·α(n)) czasu.

<details>
<summary>Wskazówka 1</summary>

Wszystkie zapytania dotyczą jednego, niezmiennego podziału.

</details>

<details>
<summary>Wskazówka 2</summary>

Najpierw zbuduj podział po krawędziach, potem czytaj zapytania.

</details>

<details>
<summary>Wskazówka 3</summary>

Równość korzeni jest odpowiedzią; ich konkretne numery nie są częścią kontraktu.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:DSU-03 -->
```java
static boolean[] connectedQueries(int n, int[][] edges, int[][] queries) {
    throw new UnsupportedOperationException("TODO DSU-03");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:DSU-03 -->
```java
static void verify() {
    assert Arrays.equals(connectedQueries(4, new int[][] {{0,1},{1,2}}, new int[][] {{0,2},{0,3},{3,3}}), new boolean[] {true,false,true});
    assert Arrays.equals(connectedQueries(2, new int[0][], new int[][] {{0,1},{1,1}}), new boolean[] {false,true});
    assert connectedQueries(1, new int[0][], new int[0][]).length == 0;
}
```
