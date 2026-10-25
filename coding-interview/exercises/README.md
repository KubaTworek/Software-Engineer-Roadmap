# Bank zadań — 72 nierozwiązane ćwiczenia z testami

[Mapa modułu](../README.md) · [Przewodnik wyboru algorytmu](../PRZEWODNIK.md) · [Metodyka](../METODYKA.md)

Każda z 24 kategorii ma trzy samodzielne zadania. Są to własne warianty ćwiczeń
klasycznych technik, z kontraktem zdefiniowanym tutaj. Nie zakładają dostępu do
platformy ani jej interfejsów. W momencie rozszerzenia banku wszystkie 72 metody mają
status **TODO**; testy są przygotowane, a rozwiązania pozostawiono do napisania.

| Kategoria | Zadania | Zestaw |
| --- | --- | --- |
| Hash map | HASH-01–03 | [unikalność, anagramy, kolejne liczby](01-hash-map.md) |
| Two pointers | TWO-01–03 | [unikalne wartości, kwadraty, liczba par](02-two-pointers.md) |
| Sliding window | WIN-01–03 | [stałe okno, różne znaki, minimalny fragment](03-sliding-window.md) |
| Prefix sum | PRE-01–03 | [zapytania, równowaga, podzielność](04-prefix-sum.md) |
| Binary search | BIN-01–03 | [granice wartości, pierwiastek, pojemność](05-binary-search.md) |
| Przedziały | INT-01–03 | [wstawienie, przecięcie, sale](06-intervals.md) |
| Monotonic stack | STACK-01–03 | [cykliczny następnik, rozpiętość, histogram](07-monotonic-stack.md) |
| Fast/slow | FAST-01–03 | [początek cyklu, długość cyklu, środek](08-fast-slow.md) |
| Heap | HEAP-01–03 | [największe, najbliższe punkty, scalanie źródeł](09-heap.md) |
| Drzewa | TREE-01–03 | [inorder, zbalansowanie, średnica](10-trees.md) |
| Grafy/BFS | BFS-01–03 | [osiągalność, wyspy, ścieżka](11-graphs-bfs.md) |
| Topological sort | TOPO-01–03 | [wykonalność, minimalny porządek, warstwy](12-topological-sort.md) |
| Backtracking | BACK-01–03 | [kombinacje, permutacje, nawiasy](13-backtracking.md) |
| DP | DP-01–03 | [nieprzyległe elementy, siatka, edycja](14-dynamic-programming.md) |
| Greedy | GREEDY-01–03 | [usuwanie kolizji, skoki, strzały](15-greedy.md) |
| Union-Find | DSU-01–03 | [zbędne krawędzie, zmienne składowe, zapytania](16-union-find.md) |
| Trie | TRIE-01–03 | [prefiksy, liczniki, budowane słowa](17-trie.md) |
| Bity | BIT-01–03 | [liczba bitów, brakująca liczba, dwie pojedyncze](18-bit-operations.md) |
| Monotonic deque | DEQUE-01–03 | [minimum okna, różnica, suma z ujemnymi](19-monotonic-deque.md) |
| Dijkstra | DIJK-01–03 | [sieć, koszt siatki, rabat](20-dijkstra.md) |
| Listy | LIST-01–03 | [odwracanie, scalanie, usuwanie](21-linked-lists.md) |
| Stos i kolejka | SQ-01–03 | [nawiasy, RPN, FIFO](22-stack-queue.md) |
| BST | BST-01–03 | [walidacja, k-ty element, suma zakresu](23-bst.md) |
| Siatki | GRID-01–03 | [kolorowanie, przekątne, wyszukiwanie słowa](24-grids.md) |

Każde zadanie ma **poziom L1–L3**, wymagania oraz **trzy ukryte wskazówki**.
Poziomy są lokalnym podziałem dydaktycznym, a nie oceną trudności platformy.
Sposób używania opisują [zasady treningu](../NAUKA.md). Po nauce kategorii przejdź
do [treningów mieszanych](../practice/README.md), które ukrywają jej nazwę.

## Konwencja fragmentów

W każdym zadaniu są dwa oznaczone bloki Java: szkielet metody i testy `verify()`.
Skrypt kompiluje każde zadanie jako osobny kontekst, z importami `java.util.*`.
Nie przenoś rozwiązań do osobnych klas. Zastąp wyjątek TODO implementacją wewnątrz
odpowiedniego bloku Markdown; możesz dopisać tam własne metody pomocnicze.

Dla list i drzew typ Node oraz konstrukcja danych są częścią szkieletu lub testów.
To infrastruktura wejścia, nie implementacja szukanego algorytmu. Testy posługują
się asercjami Javy, uruchamianymi z `-ea`. Zakładamy wejścia nie-null, chyba że
kontrakt mówi o pustym korzeniu lub końcu listy. Wartości poza podanymi warunkami
nie wymagają walidacji, o ile zadanie jawnie nie żąda wyjątku.

## Uruchamianie

PowerShell 7 i JDK 21+, z katalogu `coding-interview`:

```powershell
# Kompilacja wszystkich szkieletów i testów; nie uruchamia metod TODO.
pwsh -File scripts/check-exercises.ps1

# Testy jednego zadania po napisaniu rozwiązania.
pwsh -File scripts/check-exercises.ps1 -Exercise HASH-01

# Testy metod bez TODO; pozostałe kompilują się i są raportowane jako TODO.
pwsh -File scripts/check-exercises.ps1 -RunSolved

# Próba całego banku: raportuje wszystkie błędy i TODO, potem zwraca błąd.
pwsh -File scripts/check-exercises.ps1 -RunAll
```

Można dodać `-JdkHome 'C:/Program Files/Java/jdk-23'`. Skrypt tworzy źródła i klasy
wyłącznie w unikalnym katalogu tymczasowym i usuwa je po pracy. Nie potrzebujesz
Mavena, Gradle ani JUnit.

Domyślny sukces oznacza **poprawną strukturę i kompilację**, nie zaliczone zadania.
W raporcie PASS oznacza wykonane i zaliczone verify(), FAIL błąd kompilacji lub
testów, TODO niedokończoną metodę, COMPILED kompilację bez wykonania. Brak słowa
TODO w bloku szkieletu nie dowodzi ukończenia — dopiero uruchomienie wykonuje testy.
Usuń znacznik po napisaniu rozwiązania; pozostawiony komentarz TODO również
spowoduje pominięcie przez -RunSolved. Bez rozwiązań tryb ten zwraca sukces kontroli
kompilacji i jawnie informuje, że nie wykonał testów zachowania.
Wybrane nierozwiązane zadanie powinno zgłosić `UnsupportedOperationException`;
to oczekiwany stan początkowy, a nie zielony wynik.

## Jak pracować z testami

Najpierw ręcznie wyprowadź oczekiwane wyniki z kontraktu. Dopisz własny trudny
przypadek, zanim poprawisz implementację. Nie usuwaj asercji, aby zaliczyć zadanie;
jeżeli wykryjesz błąd oczekiwania, uzasadnij zmianę kontraktem i minimalnym przykładem.
Przy wielu poprawnych odpowiedziach testuj własności zamiast przypadkowej kolejności.

Zestawy obejmują zwykłe przypadki, puste lub minimalne wejścia, duplikaty, granice
wartości i wybrane gwarancje braku mutacji. Nie są pełnym zestawem ukrytych testów
platformy, benchmarkiem ani dowodem złożoności. Wybrane zadania porównują wynik
z brute force na małych danych ze stałym ziarnem; drzewa i grafy mają również
przypadki głębokich łańcuchów. Zakres opisuje [przewodnik testów](../TESTOWANIE.md).
Docelowy koszt w zadaniu jest
osobnym wymaganiem do uzasadnienia w notatce po rozwiązaniu.
