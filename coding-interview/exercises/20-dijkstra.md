# Zadania: Dijkstra

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/20-dijkstra.md)

Każde zadanie jest niezależne; metody pozostają TODO. Importy java.util.* zapewnia skrypt.

## DIJK-01 — czas dostarczenia do całej sieci

**Poziom:** L1
**Wymagania:** [Dijkstra](../patterns/20-dijkstra.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Graf skierowany ma n wierzchołków 0..n-1 i krawędzie [u,v,w] z nieujemną wagą int. Zwróć największy z najkrótszych dystansów od source; jeśli ktoś jest nieosiągalny, zwróć -1. n≥1, n,E≤100 000, indeksy poprawne, bez mutacji. Wynik long. Docelowo O(n + E log(E+2)) czasu.

<details>
<summary>Wskazówka 1</summary>

Potrzebujesz najkrótszego dystansu do każdego odbiorcy, nie tylko jednej drogi.

</details>

<details>
<summary>Wskazówka 2</summary>

Odbiorca pierwszy dodany do kopca nie ma jeszcze ostatecznej odległości.

</details>

<details>
<summary>Wskazówka 3</summary>

Po relaksacjach sprawdź nieosiągalność, a dopiero potem maksimum.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:DIJK-01 -->
```java
static long networkDelay(int n, int[][] edges, int source) {
    throw new UnsupportedOperationException("TODO DIJK-01");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:DIJK-01 -->
```java
static void verify() {
    assert networkDelay(3, new int[][] {{0,1,10},{0,2,1},{2,1,1}}, 0) == 2;
    assert networkDelay(2, new int[0][], 0) == -1;
    assert networkDelay(1, new int[0][], 0) == 0;
    assert networkDelay(2, new int[][] {{0,1,0}}, 0) == 0;
    assert networkDelay(3, new int[][] {{0,1,Integer.MAX_VALUE},{1,2,Integer.MAX_VALUE}}, 0) == 4294967294L;
}
```

## DIJK-02 — najtańsza droga po siatce

**Poziom:** L2
**Wymagania:** [Dijkstra](../patterns/20-dijkstra.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Niepusta prostokątna siatka zawiera nieujemne koszty int. Idziesz bokami od lewego górnego do prawego dolnego pola; płacisz za każde odwiedzone pole, także start. Zwróć minimalny koszt long. Do 100 000 pól, bez mutacji. Docelowo O(RC log(RC+2)) czasu i O(RC) pamięci.

<details>
<summary>Wskazówka 1</summary>

Ruchy mają różny koszt, więc liczba kroków nie wyznacza odpowiedzi.

</details>

<details>
<summary>Wskazówka 2</summary>

Polem stanu jest współrzędna; koszt wejścia do sąsiada to jego wartość.

</details>

<details>
<summary>Wskazówka 3</summary>

Zainicjalizuj dystans startu jego kosztem, użyj kopca i pomijaj nieaktualne wpisy.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:DIJK-02 -->
```java
static long minCostGrid(int[][] costs) {
    throw new UnsupportedOperationException("TODO DIJK-02");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:DIJK-02 -->
```java
static void verify() {
    assert minCostGrid(new int[][] {{1,9,1},{1,1,1}}) == 4;
    assert minCostGrid(new int[][] {{7}}) == 7;
    assert minCostGrid(new int[][] {{0,0},{0,0}}) == 0;
    assert minCostGrid(new int[][] {{Integer.MAX_VALUE,Integer.MAX_VALUE}}) == 4294967294L;
    int[][] input = {{2,8},{1,3}};
    assert minCostGrid(input) == 6;
    assert Arrays.deepEquals(input, new int[][] {{2,8},{1,3}});
}
```

## DIJK-03 — jeden opcjonalny rabat

**Poziom:** L3
**Wymagania:** [Dijkstra](../patterns/20-dijkstra.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Graf skierowany opisują [u,v,w] z nieujemnymi wagami int. Od source do target wolno co najwyżej raz zapłacić floor(w/2) zamiast w za jedną krawędź. Zwróć minimalny koszt long lub -1. Source=target daje 0. n≥1,n,E≤100 000, bez mutacji. Docelowo O(n + E log(E+2)) czasu i O(n+E) pamięci.

<details>
<summary>Wskazówka 1</summary>

Dojście do tego samego wierzchołka z zachowanym i zużytym rabatem ma inną przyszłość.

</details>

<details>
<summary>Wskazówka 2</summary>

Rozszerz stan o bit informujący, czy rabat został wykorzystany.

</details>

<details>
<summary>Wskazówka 3</summary>

Z każdego stanu dodaj zwykły ruch; z niewykorzystanego także ruch z połową kosztu.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:DIJK-03 -->
```java
static long discountedPath(int n, int[][] edges, int source, int target) {
    throw new UnsupportedOperationException("TODO DIJK-03");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:DIJK-03 -->
```java
static void verify() {
    assert discountedPath(3, new int[][] {{0,1,8},{1,2,8},{0,2,25}}, 0, 2) == 12;
    assert discountedPath(2, new int[][] {{0,1,5}}, 0, 1) == 2;
    assert discountedPath(2, new int[0][], 0, 1) == -1;
    assert discountedPath(1, new int[0][], 0, 0) == 0;
    assert discountedPath(2, new int[][] {{0,1,0}}, 0, 1) == 0;
}
```
