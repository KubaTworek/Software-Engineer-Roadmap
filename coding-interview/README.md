# Coding Interview — algorytmy i coding patterns w Javie

Moduł do nauki rozwiązywania zadań w stylu LeetCode. Celem jest przejście od treści
problemu do poprawnego algorytmu: rozpoznanie struktury, uzasadnienie decyzji,
zapis kodu i sprawdzenie przypadków brzegowych. Nazwa schematu jest skrótem
rozumowania, a nie dowodem, że pasuje do zadania.

Forma nawiązuje do [design-patterns](../design-patterns/README.md): samodzielne
karty z mechanizmem, przykładem, kosztem i ograniczeniami. Tutaj wzorzec oznacza
technikę algorytmiczną, np. sliding window, a nie wzorzec projektowania klas GoF.

## Od czego zacząć

1. Przeczytaj [metodykę](METODYKA.md): kontrakt → brute force → obserwacja →
   niezmiennik → kod → weryfikacja.
2. Przejdź przez [poradnik wyboru algorytmu](PRZEWODNIK.md): sygnały w treści,
   warunki użycia technik i odrzucanie błędnych hipotez.
3. Powtórz [struktury, złożoność i pułapki Javy](JAVA.md).
4. Wybierz kartę z tabeli i prześledź przykład bez uruchamiania kodu.
5. Rozwiąż zadanie z [banku 72 nierozwiązanych ćwiczeń](exercises/README.md)
   i uruchom jego testy. [Plan ćwiczeń](PLAN.md) porządkuje kategorie i powtórki.
6. Korzystaj z [materiałów](MATERIALY.md), gdy brakuje konkretnego mechanizmu.

## Katalog: 25 opracowanych kart

| # | Karta | Sygnał w zadaniu | Przykład |
| --- | --- | --- | --- |
| 1 | [Hash map](patterns/01-hash-map.md) | potrzebny wcześniej widziany element lub licznik | Two Sum |
| 2 | [Two pointers](patterns/02-two-pointers.md) | uporządkowane dane pozwalają odrzucać kandydatów | para o zadanej sumie |
| 3 | [Sliding window](patterns/03-sliding-window.md) | spójny fragment i naprawialny warunek okna | najdłuższy fragment bez powtórzeń |
| 4 | [Prefix sum](patterns/04-prefix-sum.md) | sumy zakresów lub liczenie fragmentów | liczba fragmentów o sumie k |
| 5 | [Binary search](patterns/05-binary-search.md) | monotoniczny warunek na uporządkowanej dziedzinie | pierwsza pozycja z wartością ≥ target |
| 6 | [Sortowanie i przedziały](patterns/06-intervals.md) | relacje zakresów upraszczają się po sortowaniu | scalanie przedziałów |
| 7 | [Monotonic stack](patterns/07-monotonic-stack.md) | najbliższy większy lub mniejszy element | dni do wyższej temperatury |
| 8 | [Fast and slow pointers](patterns/08-fast-slow.md) | wielokrotne przejście po następniku | cykl w liście |
| 9 | [Heap / top-k](patterns/09-heap.md) | potrzebne tylko k najlepszych elementów | k-ty największy element |
| 10 | [Drzewa i DFS](patterns/10-trees.md) | wynik węzła składa się z wyników poddrzew | wysokość drzewa |
| 11 | [Grafy i BFS](patterns/11-graphs-bfs.md) | najkrótsza ścieżka o jednakowym koszcie krawędzi | odległość w grafie nieważonym |
| 12 | [Topological sort](patterns/12-topological-sort.md) | kolejność zależności i wykrywanie cyklu | harmonogram zadań |
| 13 | [Backtracking](patterns/13-backtracking.md) | wszystkie dopuszczalne kombinacje | podzbiory zbioru |
| 14 | [Dynamic programming](patterns/14-dynamic-programming.md) | te same podproblemy i wystarczający stan | minimalna liczba monet |
| 15 | [Greedy](patterns/15-greedy.md) | lokalny wybór można uzasadnić wymianą | maksymalny zbiór spotkań |
| 16 | [Union-Find](patterns/16-union-find.md) | dodawanie połączeń i pytania o składowe | łączenie zbiorów |
| 17 | [Trie](patterns/17-trie.md) | wiele słów współdzieli prefiksy | słownik i prefiksy |
| 18 | [Operacje bitowe](patterns/18-bit-operations.md) | liczności lub stan dają się wyrazić bitami | XOR par |
| 19 | [Monotonic deque](patterns/19-monotonic-deque.md) | ekstremum aktualnego okna | maksimum każdego okna |
| 20 | [Dijkstra](patterns/20-dijkstra.md) | najtańsza droga przy nieujemnych wagach | relaksacje z kopcem |
| 21 | [Listy](patterns/21-linked-lists.md) | zmieniasz kolejność połączeń | odwracanie referencji |
| 22 | [Stos i kolejka](patterns/22-stack-queue.md) | LIFO albo FIFO wynika z kolejności pracy | kolejka przez dwa stosy |
| 23 | [BST](patterns/23-bst.md) | porządek dotyczy całych poddrzew | walidacja granic |
| 24 | [Siatki](patterns/24-grids.md) | sąsiedzi są opisani współrzędnymi | ścieżka w labiryncie |
| 25 | [Ewolucja DP](patterns/25-dp-evolution.md) | powtarzająca się rekurencja | memoizacja → tabulacja → mały stan |

Każda karta zawiera własny kontrakt, punkt wyjścia brute force, niezmiennik,
przebieg na danych, kod, koszt, kontrprzykład i ćwiczenie. 24 kategorie zadań
odpowiadają kartom 01–24; karta 25 rozwija naukę DP. Zaawansowane DP i dalsze
algorytmy grafowe pozostają poza zakresem. Binary search po odpowiedzi ma
wprowadzenie w poradniku i zadanie BIN-03.

## Poradnik i zadania do samodzielnego rozwiązania

[Przewodnik](PRZEWODNIK.md) prowadzi od treści i ograniczeń do hipotezy algorytmu.
Wyjaśnia dobór struktury, warunek poprawności i typowe pomyłki między technikami.
[Bank zadań](exercises/README.md) zawiera **72 ćwiczenia w 24 kategoriach**.
Każde ma kontrakt, ograniczenia, szkielet TODO i testy w Markdown. Nie zawiera
implementacji rozwiązań. Zestawy testów kompilują się niezależnie dla każdego zadania.
Każde zadanie ma poziom L1–L3, wymagania wstępne i trzy rozwijane wskazówki.
[Zasady treningu](NAUKA.md) wyjaśniają ich użycie oraz ocenę samodzielności.
[Cztery treningi mieszane](practice/README.md) po sześć zadań ukrywają kategorię
do rozwinięcia podpowiedzi. To osobne kopie robocze z własnymi metodami i testami;
generator pozwala przygotować następne zestawy.
Generator filtruje poziomy, poznane kategorie i wykluczone identyfikatory;
opcjonalnie pomija już uzupełnione metody. Każdy zestaw ma ślad pochodzenia
i skróty treści, testów oraz szkieletu. Kontrola zgodności informuje o rozjazdach
i nie nadpisuje własnych rozwiązań.

[Osiem ćwiczeń diagnostycznych](debugging/README.md) polega na znalezieniu
kontrprzykładu i naprawie kompilującego się, błędnego kodu. Są osobnym zestawem,
nie zwiększają liczby 72 nierozwiązanych zadań algorytmicznych.

## Kod i weryfikacja

Kod jest zapisany wyłącznie w Markdown. Bloki `java` w kartach są składowymi
klasy: metody statyczne oraz ewentualne typy pomocnicze. Każda karta ma osobny
kontekst; nie należy sklejać typów pomocniczych różnych kart w jedną klasę.
Importy z `java.util.*` zapewnia skrypt kontrolny. `verify()` zawiera wykonywane
asercje; pozostałe opisane ćwiczenia są zadaniami do samodzielnego wykonania.
Osobny katalog exercises/ zawiera nierozwiązane metody i ich własne verify();
nie należy ich utożsamiać z gotowymi przykładami patterns/.

Z katalogu `coding-interview`, PowerShell 7 oraz JDK 21+ w PATH:

```powershell
pwsh -File scripts/check-examples.ps1

# Bank zadań: kompilacja szkieletów i testów, bez wykonania metod TODO.
pwsh -File scripts/check-exercises.ps1

# Testy konkretnego ćwiczenia po jego rozwiązaniu.
pwsh -File scripts/check-exercises.ps1 -Exercise HASH-01

# Testy uzupełnionych metod; TODO pozostają jawnie pominięte.
pwsh -File scripts/check-exercises.ps1 -RunSolved

# Samotesty skryptów na sztucznych przykładach PASS / FAIL / TODO.
pwsh -File scripts/test-runner.ps1

# Zgodność treningów i testy filtrów oraz ochrony własnej pracy.
pwsh -File scripts/check-practice.ps1
pwsh -File scripts/test-practice.ps1

# Kontrola oczekiwań testów; ten obszar zawiera rozwiązania referencyjne.
pwsh -File scripts/check-test-quality.ps1
```

Jeśli JDK nie jest dostępny przez JAVA_HOME lub PATH, wskaż katalog instalacji
parametrem `-JdkHome`, np. `-JdkHome 'C:/Program Files/Java/jdk-23'`.

Skrypt wyciąga bloki z kart do unikalnego katalogu tymczasowego poza modułem,
kompiluje je z `--release 21`, uruchamia `verify()` z włączonymi asercjami i usuwa
pliki tymczasowe. W repozytorium nie ma źródeł `.java`, klas `.class`, Mavena,
Gradle ani aplikacji. Kompilacja fragmentów jest kontrolą przykładów.

Z korzenia repozytorium:

```powershell
pwsh -File scripts/check-documentation.ps1
git diff --check
```

Checker odsyłaczy działa offline; nie sprawdza dostępności stron zewnętrznych.
Asercje w kartach sprawdzają wybrane zachowania, nie stanowią pełnego dowodu
poprawności. Uzasadnienie niezmiennika czytaj razem z kodem.
Domyślny tryb check-exercises.ps1 potwierdza wyłącznie kompilację, raportując
liczbę TODO. Uruchomienie nierozwiązanego zadania z -Exercise kończy się błędem
TODO, zgodnie z jego początkowym statusem. Nie ma zaliczonych testów zachowania
dla pustych implementacji.
Raport rozróżnia PASS, FAIL, TODO i COMPILED (kompilacja bez wykonania).
Tryb -RunAll zbiera cały raport i kończy się błędem, gdy pozostało FAIL lub TODO.
Wybór -Exercise kompiluje i uruchamia tylko wskazane zadanie. Parser sprawdza
strukturę całego wybranego dokumentu lub banku; nie narzuca liczby kategorii ani zadań.
Kompilator i JVM pracują na plikach tymczasowych, a raport błędu wskazuje plik
Markdown i rzeczywistą linię kodu lub asercji. Ramki samego wygenerowanego
otoczenia są osobno oznaczone. Dotyczy to przykładów, ćwiczeń i referencji.

Kontrola testów obejmuje obecnie **80 referencji i 32 mutacje**: 72 zadania
algorytmiczne oraz 8 diagnostycznych. [Obszar verification/](verification/README.md)
zawiera pełne rozwiązania, więc otwieraj go po własnej próbie. Testy oryginalnego
banku są wykonywane na referencjach bez zastępowania metod TODO w banku.
Wykrycie wszystkich przygotowanych mutacji nie jest dowodem kompletności testów.

[Kontrole testów](TESTOWANIE.md) opisują porównania z brute force, duże wejścia
i ograniczenia sprawdzania. [Workflow CI](../.github/workflows/coding-interview-ci.yml)
używa JDK 21 i uruchamia przykłady, kontrolę banku, samotesty, zestawy mieszane
oraz checker dokumentacji. Samo dodanie workflow nie oznacza wykonania zdalnego CI.

## Miejsce w repozytorium

[Interview preparation](../interview-preparation/README.md) służy powtórkom pytań
technicznych, [LLD](../low-level-design/README.md) projektowaniu współpracujących
klas, a ten moduł ćwiczy krótkie algorytmy i ich analizę. Do rozwiązania zadania
wystarczają opisany kontrakt, struktury danych i standardowa biblioteka Javy.
