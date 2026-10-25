# Zadania: topological sort

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/12-topological-sort.md)

Grafy mają poprawne indeksy, mogą mieć cykle, pętle i powtarzające się krawędzie.
V i E do 100 000. Wejścia pozostają bez zmian. Listy sąsiedztwa nie zawierają null.

## TOPO-01 — czy wszystkie kursy są wykonalne

**Poziom:** L1
**Wymagania:** [Karta techniki](../patterns/12-topological-sort.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Sprzeczność jest cyklem w dowolnej składowej.

</details>

<details>
<summary>Wskazówka 2</summary>

Para ma kierunek prerequisite→course.

</details>

<details>
<summary>Wskazówka 3</summary>

Porównaj liczbę przetworzonych wierzchołków z liczbą kursów.

</details>

**Treść i kontrakt.** Kursy mają numery 0..courses-1. Para `[course, prerequisite]`
oznacza, że prerequisite musi zostać ukończony przed course. Sprawdź, czy można
ukończyć wszystkie kursy. Dla 0 kursów i braku par wynik to true. Brak zależności
nie blokuje kursu. Docelowo O(V + E) czasu, O(V + E) pamięci łącznie z reprezentacją
grafu utworzoną z par.

Kontekst: nierozwiązany szkielet. Zwróć uwagę na kolejność znaczeń w parze.

<!-- exercise:TOPO-01 -->
```java
static boolean canFinish(int courses, int[][] prerequisites) {
    throw new UnsupportedOperationException("TODO TOPO-01");
}
```

Kontekst: wykonywalne testy.

<!-- tests:TOPO-01 -->
```java
static void verify() {
    int[][] input = {{1, 0}, {2, 1}};
    assert canFinish(3, input);
    assert Arrays.deepEquals(input, new int[][] {{1, 0}, {2, 1}});
    assert !canFinish(2, new int[][] {{1, 0}, {0, 1}});
    assert !canFinish(1, new int[][] {{0, 0}});
    assert !canFinish(4, new int[][] {{1, 0}, {2, 3}, {3, 2}});
    assert canFinish(2, new int[][] {{1, 0}, {1, 0}});
    assert canFinish(5, new int[0][]);
    assert canFinish(0, new int[0][]);
}
```

## TOPO-02 — najmniejszy leksykograficznie harmonogram

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/12-topological-sort.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Nie każdy poprawny porządek jest najmniejszy.

</details>

<details>
<summary>Wskazówka 2</summary>

Potrzebujesz minimum spośród aktualnie gotowych wierzchołków.

</details>

<details>
<summary>Wskazówka 3</summary>

Nowo gotowy mały numer może wyprzedzić wcześniej gotowy większy.

</details>

**Treść i kontrakt.** Dla grafu zależności w listach sąsiedztwa (u→v: u przed v)
zwróć najmniejszy leksykograficznie poprawny porządek wszystkich wierzchołków.
Spośród aktualnie możliwych prefiksów wybierany porządek ma mieć najmniejszy numer
w pierwszym miejscu różnicy. Cykl → pusta tablica; pusty graf → pusta tablica.
Docelowo O(E + V log(V + 1)) czasu, O(V) pamięci poza wejściem i wynikiem.

Kontekst: nierozwiązany szkielet.

<!-- exercise:TOPO-02 -->
```java
static int[] smallestOrder(int[][] graph) {
    throw new UnsupportedOperationException("TODO TOPO-02");
}
```

Kontekst: wykonywalne testy; zwykła poprawność topologiczna tu nie wystarcza.

<!-- tests:TOPO-02 -->
```java
static void verify() {
    int[][] graph = {{2}, {2}, {3}, {}, {}};
    assert Arrays.equals(smallestOrder(graph), new int[] {0, 1, 2, 3, 4});
    assert Arrays.deepEquals(graph, new int[][] {{2}, {2}, {3}, {}, {}});
    assert Arrays.equals(smallestOrder(new int[][] {{1}, {}, {}}), new int[] {0, 1, 2});
    assert Arrays.equals(smallestOrder(new int[][] {{}, {}, {}}), new int[] {0, 1, 2});
    assert Arrays.equals(smallestOrder(new int[][] {{1, 1}, {}}), new int[] {0, 1});
    assert smallestOrder(new int[][] {{1}, {0}}).length == 0;
    assert smallestOrder(new int[0][]).length == 0;
}
```

## TOPO-03 — najwcześniejsze semestry

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/12-topological-sort.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Kurs odblokowany teraz zaczyna dopiero następny semestr.

</details>

<details>
<summary>Wskazówka 2</summary>

Oddziel aktualną warstwę od nowo odblokowanej.

</details>

<details>
<summary>Wskazówka 3</summary>

Zapisuj warstwy rosnąco; pozostały cykl unieważnia cały plan.

</details>

**Treść i kontrakt.** Każdy kurs trwa jeden semestr; równolegle można uczyć się
dowolnej liczby kursów. Wymagany poprzednik musi zakończyć się przed rozpoczęciem
semestru następcy. Dla grafu u→v zwróć listę semestrów, umieszczając każdy kurs
w najwcześniejszym możliwym. Numery w semestrze mają rosnąć. Jeśli istnieje cykl,
zwróć pustą listę, bez częściowego planu. Pusty graf → pusta lista.
Docelowo O(E + V log(V + 1)) czasu z porządkowaniem warstw, O(V) pamięci poza wynikiem.

Kontekst: nierozwiązany szkielet.

<!-- exercise:TOPO-03 -->
```java
static List<List<Integer>> semesterLayers(int[][] graph) {
    throw new UnsupportedOperationException("TODO TOPO-03");
}
```

Kontekst: wykonywalne testy; kurs odblokowany teraz należy dopiero do kolejnego semestru.

<!-- tests:TOPO-03 -->
```java
static void verify() {
    assert semesterLayers(new int[][] {{2}, {2}, {3}, {}, {}})
            .equals(List.of(List.of(0, 1, 4), List.of(2), List.of(3)));
    assert semesterLayers(new int[][] {{1}, {2}, {}})
            .equals(List.of(List.of(0), List.of(1), List.of(2)));
    assert semesterLayers(new int[][] {{}, {}, {}}).equals(List.of(List.of(0, 1, 2)));
    assert semesterLayers(new int[][] {{1, 1}, {}}).equals(List.of(List.of(0), List.of(1)));
    assert semesterLayers(new int[][] {{}, {2}, {1}}).isEmpty();
    assert semesterLayers(new int[0][]).isEmpty();
}
```
