# Plan ćwiczeń i powtórek

[Mapa modułu](README.md) · [Metodyka](METODYKA.md) · [Materiały](MATERIALY.md)

To kolejność treningowa bez sztywnego kalendarza. Przed każdym etapem wybierz
kartę z [katalogu](README.md), a po niej rozwiąż wariant bez zaglądania do kodu.
Lokalny [bank ćwiczeń](exercises/README.md) ma po trzy nierozwiązane zadania na
każdą kategorię, z testami i szkieletem metody. Najpierw wykonaj analizę według
[przewodnika](PRZEWODNIK.md), potem uzupełnij metodę i uruchom jej testy.
Nazwy i numery poniżej pomagają odnaleźć zadania na LeetCode; obowiązuje ich
aktualny kontrakt, nie uproszczenia naszych przykładów. Bankiem dalszych prób jest
[oficjalny plan LeetCode](https://leetcode.com/studyplan/top-interview-150/).

## Lokalna ścieżka i wymagania wstępne

Najpierw wykonuj L1, potem L2, a L3 po samodzielnym wyjaśnieniu niezmiennika.
Trzy rozwijane wskazówki i poziom w każdej sekcji opisują [zasady treningu](NAUKA.md).

| Krok | Karty i zależności | Próby lokalne |
| --- | --- | --- |
| 0 | [Java](JAVA.md), [stos/kolejka](patterns/22-stack-queue.md), [listy](patterns/21-linked-lists.md) | SQ-01, LIST-01; potem SQ-02–03, LIST-02–03 |
| 1 | hash map, two pointers | HASH-01–02, TWO-01; potem HASH-03, TWO-02–03 |
| 2 | okna, prefiksy, binary search | WIN-01, PRE-01, BIN-02; potem pozostałe z tych kategorii |
| 3 | przedziały, stos monotoniczny, fast/slow, heap | najpierw FAST-03, potem L2; STACK-03 i HEAP-02 po opanowaniu podstaw |
| 4 | drzewa i DFS, [BST](patterns/23-bst.md) | TREE-01, BST-01; potem TREE-02–03 i BST-02–03 |
| 5 | grafy/BFS, [siatki](patterns/24-grids.md), topological sort | BFS-01, GRID-01, TOPO-01; potem ścieżki i warstwy; GRID-03 po backtrackingu |
| 6 | backtracking, [ewolucja DP](patterns/25-dp-evolution.md), DP i greedy | BACK-01, DP-01–02, GREEDY-01; potem warianty L2–L3 |
| 7 | [DSU](patterns/16-union-find.md), [Trie](patterns/17-trie.md), [bity](patterns/18-bit-operations.md) | DSU-01–03, TRIE-01–03, BIT-01–03 |
| 8 | [deque](patterns/19-monotonic-deque.md) po oknach/prefiksach, [Dijkstra](patterns/20-dijkstra.md) po grafach/kopcu | DEQUE-01–03, DIJK-01–03 |
| 9 | samodzielne rozpoznawanie | [cztery zestawy mieszane](practice/README.md), potem własny zestaw z generatora |
| 10 | czytanie i naprawianie kodu | [osiem ćwiczeń diagnostycznych](debugging/README.md): kontrprzykład, niezmiennik, poprawka i regresja |

Kolejność jest propozycją zależności, nie wymogiem zaliczenia wszystkich zadań
przed kolejną kategorią. Po próbie odnotuj poziom wykorzystanej wskazówki, wynik
testów i warunek, którego zmiana wymagałaby innej techniki.

## Dalsze zadania na platformie

| Etap | Karty | Próba podstawowa | Wariant do samodzielnego rozwiązania |
| --- | --- | --- | --- |
| 1 | 01–02: hash map i two pointers | #1 Two Sum; #167 Two Sum II | porównaj pamięć i zachowanie indeksów po sortowaniu |
| 2 | 03–04: okna i prefiksy | #3 Longest Substring Without Repeating Characters; #560 Subarray Sum Equals K | wyjaśnij, dlaczego liczby ujemne psują okno oparte wyłącznie na sumie |
| 3 | 05–06: szukanie i przedziały | #35 Search Insert Position; #56 Merge Intervals | znajdź obie granice duplikatów; zmień przedziały domknięte na półotwarte |
| 4 | 07–09: stos, wskaźniki, kopiec | #739 Daily Temperatures; #141 Linked List Cycle; #215 Kth Largest Element in an Array | znajdź początek cyklu; utrzymuj top-k dla strumienia |
| 5 | 10–12: drzewa i grafy | #104 Maximum Depth of Binary Tree; #207 Course Schedule | zapisz DFS iteracyjnie; dla BFS odtwórz ścieżkę przez tablicę parent |
| 6 | 13–15: wybory i optymalizacja | #78 Subsets; #322 Coin Change; #435 Non-overlapping Intervals | obsłuż powtórzenia w podzbiorach; pokaż kontrprzykład dla zachłannego wydawania monet |

Dla #435 liczba usuniętych przedziałów wynika z maksymalnej liczby zachowanych;
karta greedy zwraca tę drugą wielkość. Dla #35 lower bound odpowiada miejscu
wstawienia przy jego założeniach. Nie zakładaj, że identyczny jest cały interfejs.

## Kiedy przejść dalej

- Umiesz opowiedzieć brute force i wskazać powtarzaną pracę.
- Formułujesz niezmiennik bez odwoływania się do nazwy wzorca.
- Potrafisz prześledzić przykład i kontrprzykład, podać czas oraz pamięć.
- Zapisujesz rozwiązanie bez patrzenia w kartę i umiesz zmienić jeden warunek.

Po poznaniu kilku kart mieszaj zadania bez podpowiedzi kategorii. Rozpoznanie
techniki jest osobną umiejętnością od zapisania znanego szablonu. Liczby
rozwiązanych zadań traktuj jako zapis prób, nie miarę gotowości do rozmowy.

## Szablon własnej notatki

Poniższy blok jest szablonem opisowym do skopiowania, nie kodem do uruchomienia.

```text
Zadanie i wariant kontraktu:
Brute force i koszt:
Obserwacja usuwająca pracę:
Niezmiennik / stan DP:
Dlaczego rozwiązanie jest kompletne i kończy działanie:
Czas; pamięć pomocnicza; pamięć wyniku:
Mój błąd i minimalny kontrprzykład:
Zmiana warunku, przy której trzeba zmienić algorytm:
Data samodzielnej powtórki i wynik:
```
