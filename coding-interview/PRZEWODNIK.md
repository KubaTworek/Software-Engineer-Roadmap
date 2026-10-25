# Od treści zadania do wyboru algorytmu

[Mapa modułu](README.md) · [Metodyka pracy](METODYKA.md) · [Zadania bez rozwiązań](exercises/README.md)

Przeczytaj treść, zbuduj model operacji, a dopiero potem nazwij technikę.
Słowa „minimum”, „para” albo „podciąg” są początkiem analizy. O wyborze decydują
własności danych i wyniku, które pozwalają ograniczyć sprawdzanych kandydatów.
Nie każde zadanie ma jedną właściwą technikę; porównuj poprawne warianty pod
względem ograniczeń czasu, pamięci i mutacji wejścia.

## Pierwsze pięć minut po przeczytaniu treści

1. **Powiedz własnymi słowami, co zwracasz.** Wartość, indeksy, liczbę wyników,
   wszystkie wyniki czy odpowiedź tak/nie? „Znajdź parę” i „policz wszystkie pary”
   mogą korzystać z podobnego pomysłu, lecz wymagają innego stanu dla duplikatów.
2. **Wypisz warunki.** Rozmiar, zakres wartości, znaki liczb, sortowanie, duplikaty,
   dostęp losowy albo strumień, alfabet, możliwość zmiany danych. Zapisz osobno
   warunki gwarantowane w treści i własne założenia wymagające doprecyzowania.
3. **Narysuj mały przykład i najmniejszy trudny przypadek.** Obok zwykłego wejścia
   postaw pusty wynik, powtarzający się element, cykl albo liczbę ujemną.
4. **Nazwij najprostszy poprawny przegląd.** Jak wyglądają kandydaci? Pary, fragmenty,
   podzbiory, ścieżki, kolejności? Ile ich jest i co liczysz wielokrotnie?
5. **Zapisz hipotezę optymalizacji i warunek, który ją umożliwia.** Np. „sortowanie
   pozwala odrzucić wszystkie pary z tym lewym końcem”. Bez takiego zdania nazwa
   algorytmu jest zgadywaniem.

Przykładowa notatka przed kodem: „Szukam długości spójnego fragmentu; dane mają
zera i liczby ujemne; brute force rozszerza każdy początek w O(n²); suma fragmentu
jest różnicą prefiksów; muszę wiedzieć, jaki wcześniejszy stan daje żądany wynik”.
Zwróć uwagę, że cel „długość” podpowiada zachowanie najwcześniejszego indeksu,
a cel „liczba” wymagałby zachowania liczności stanów.

## Mapa wyboru: co musi być prawdą

| Sygnał po analizie kontraktu | Kandydat | Warunek i uzasadnienie | Sprawdź przed kodem |
| --- | --- | --- | --- |
| Dla elementu potrzebuję dopełnienia, licznika albo informacji „widziałem” | [Hash map](patterns/01-hash-map.md) | przeszukiwanie dotyczy klucza możliwego do zapisania | czy mapa opisuje prefiks, całe wejście czy bieżące okno? |
| Mam porządek i mogę usuwać całe grupy par | [Two pointers](patterns/02-two-pointers.md) | ruch wskaźnika nie odrzuca potencjalnego rozwiązania | czy sortowanie zmienia wymagane indeksy? |
| Szukam spójnego zakresu ze stanem aktualizowanym na końcach | [Sliding window](patterns/03-sliding-window.md) | po naruszeniu warunku mogę bezpiecznie przesuwać lewy koniec; dla stałego okna znam jego rozmiar | czy usuwanie elementu rzeczywiście naprawia warunek? |
| Pytam o sumy fragmentów, ich liczbę lub długość | [Prefix sum](patterns/04-prefix-sum.md) | wynik zakresu można odtworzyć z dwóch prefiksów | czy przechowuję licznik, pierwszy indeks, czy same sumy? |
| Warunek na uporządkowanej dziedzinie zmienia się tylko raz | [Binary search](patterns/05-binary-search.md) | istnieje podział fałsz/prawda, a krok skraca dziedzinę | co oznaczają obie granice i brak odpowiedzi? |
| Porównuję początki i końce zakresów | [Przedziały](patterns/06-intervals.md) | sortowanie umożliwia lokalną decyzję lub przegląd zdarzeń | domknięte czy półotwarte; co oznacza punkt styku? |
| Szukam pierwszego większego/mniejszego sąsiada lub granicy dla elementu | [Monotonic stack](patterns/07-monotonic-stack.md) | nowy element rozstrzyga poprzednie, a usunięte kandydatury już nie wrócą | czy równość ma zdejmować ze stosu? |
| Powtarzam przejście po jednoznacznym następniku | [Fast/slow](patterns/08-fast-slow.md) | każdy stan ma co najwyżej jednego następnika; można wykryć spotkanie | czy porównuję tożsamość węzłów, a nie ich wartości? |
| Wystarczy k najlepszych albo ciągle pobieram najbliższy priorytet | [Heap](patterns/09-heap.md) | można odrzucić gorszych bez utraty potrzebnego wyniku | minimum czy maksimum na szczycie; jaka reguła remisów? |
| Wynik węzła zależy od wyników jego dzieci | [Drzewa/DFS](patterns/10-trees.md) | struktura jest drzewem, a wynik poddrzewa ma mały opis | co zwraca null; czy potrzebny jest stan z góry czy z dołu? |
| Chcę najmniej kroków po krawędziach tego samego kosztu | [BFS](patterns/11-graphs-bfs.md) | pierwsze odkrycie wierzchołka następuje w najkrótszej warstwie | czy visited oznaczam przy dodaniu do kolejki? |
| Kolejny krok może ruszyć dopiero po poprzednikach | [Topological sort](patterns/12-topological-sort.md) | zadanie opisuje skierowane zależności i wymaga obsługi cyklu | w którą stronę biegnie krawędź i co zwrócić dla cyklu? |
| Trzeba wypisać wszystkie dopuszczalne konfiguracje | [Backtracking](patterns/13-backtracking.md) | stan gałęzi można rozszerzyć i cofnąć; wynik może być wykładniczy | czy zapisuję kopię i czy ta sama konfiguracja powstaje raz? |
| Szukam optimum/liczby sposobów, a podproblemy się powtarzają | [DP](patterns/14-dynamic-programming.md) | stan zawiera całą potrzebną informację, a zależności można policzyć w porządku | czy umiem nazwać dp i wyprowadzić przejście bez kodu? |
| Chcę podejmować nieodwracalne lokalne wybory | [Greedy](patterns/15-greedy.md) | mam argument wymiany, dominacji lub pozostawania nie gorszym od optimum | jaki mały kontrprzykład obaliłby tę regułę? |
| Dodaję połączenia i pytam o składowe | [Union-Find](patterns/16-union-find.md) | połączenie scala rozłączne zbiory | czy potrzebuję usuwać krawędzie lub odtworzyć drogę? |
| Wielokrotnie pytam o początki słów | [Trie](patterns/17-trie.md) | prefiksy współdzielą ścieżki | czy węzeł jest końcem całego słowa? |
| Liczności lub stan mają reprezentację bitową | [Bity](patterns/18-bit-operations.md) | maski/XOR zachowują potrzebną informację | jaka jest szerokość typu i rola bitu znaku? |
| Potrzebuję ekstremum przesuwanego okna | [Monotonic deque](patterns/19-monotonic-deque.md) | nowszy lepszy element dominuje starszy | które indeksy wygasły, a które są zdominowane? |
| Krawędzie mają różne nieujemne koszty | [Dijkstra](patterns/20-dijkstra.md) | najmniejszy aktualny dystans jest ostateczny | czy wpis kopca jest nadal aktualny? |
| Przepinam sekwencję referencji | [Listy](patterns/21-linked-lists.md) | zachowuję głowy reszty i gotowego wyniku | tożsamość węzłów, stabilność i cykle? |
| Potrzebuję ostatniego otwarcia albo najstarszego zgłoszenia | [Stos/kolejka](patterns/22-stack-queue.md) | kolejność LIFO/FIFO wynika z kontraktu | koszt pojedynczy czy amortyzowany? |
| Porządek dotyczy całych poddrzew | [BST](patterns/23-bst.md) | granice wynikają ze wszystkich przodków | czy duplikaty są dopuszczalne? |
| Sąsiadów opisują współrzędne | [Siatki](patterns/24-grids.md) | plansza jest grafem niejawnym | 4 czy 8 kierunków, visited globalne czy w gałęzi? |

To hipotezy, nie zamknięty katalog. Zadanie może wymagać połączenia sortowania
z two pointers, mapy z oknem, prefiksów z mapą, kopca z licznikami albo DAG z DP.
Nie wymuszaj techniki z katalogu, jeśli jej warunki nie są spełnione.

## Jak wykorzystać ograniczenia wejścia

Najpierw oszacuj pracę kandydata na największym wejściu: dla n = 100 000 wszystkie
pary oznaczają około 5 miliardów sprawdzeń. To mocny powód do szukania lepszego
wariantu. Nie przeliczaj Big O na jeden uniwersalny limit milisekund: koszt
operacji, środowisko, alokacje i limit platformy różnią się.

| Ograniczenie | Co rozważyć | Czego nie wnioskować automatycznie |
| --- | --- | --- |
| n jest małe, np. 15–20 | enumeracja podzbiorów, backtracking | że dowolna rekurencja jest tania; permutacji jest n! |
| n jest duże | liniowy przegląd, hashowanie, sortowanie, logarytmiczne zapytania | że O(n log n) zawsze spełni każdy limit |
| wartości mieszczą się w małym alfabecie | tablica liczników zamiast mapy | że długość tekstu i rozmiar alfabetu to ten sam parametr |
| dane są już posortowane | dwa wskaźniki, binary search, scalanie | że trzeba je sortować ponownie |
| trzeba wypisać wszystkie podzbiory | uwzględnienie wykładniczego rozmiaru wyniku | że DP pozwoli wypisać je w czasie wielomianowym |
| pytania powtarzają się dla stałych danych | przygotowanie prefiksów lub indeksu | że opłaca się preprocessing dla pojedynczego małego zapytania |
| dane docierają w strumieniu | ograniczony stan, liczniki, kopiec top-k | że można cofnąć się do poprzedniego elementu bez jego zapisania |

Dopisz oddzielnie rozmiar tablicy n, liczbę krawędzi E, k w top-k, kwotę A w DP
i długość wyniku. O(A·m) dla monet zależy od wartości A, nie od liczby jej bitów.

## Najczęstsze rozgałęzienia decyzji

### Sliding window czy prefix sum

Najpierw sprawdź spójność zakresu. Następnie określ, jak zmienia się warunek po
rozszerzeniu i skróceniu. Dla sumy dodatnich liczb usuwanie z lewej zmniejsza sumę;
dla ogólnych liczb całkowitych nie ma takiej gwarancji. Dla ograniczenia liczby
różnych znaków usunięcie może tę liczbę zmniejszyć, więc okno pozostaje kandydatem.

Przykład obalający bezrefleksyjne okno dla sumy co najmniej 3: `[1,-1,3]`.
Po osiągnięciu sumy 3 usunięcie pierwszej jedynki obniża sumę do 2. Zatrzymanie
skracania pomija samodzielne `[3]`. Nie wystarczy hasło „minimalny fragment”.
Prefiksy opisują sumy również z ujemnymi wartościami, ale nie każde pytanie da się
rozwiązać samą mapą; część wariantów wymaga dalszej struktury.

### BFS czy DFS

Dla osiągalności wystarczy poprawny przegląd z visited. Dla najkrótszej liczby
krawędzi BFS daje uzasadnienie warstwami. DFS jest naturalny przy agregacji
poddrzew i eksploracji wyborów, ale pierwsza znaleziona ścieżka nie musi być
najkrótsza. Różne wagi oznaczają, że samo BFS nie rozwiązuje minimalizacji kosztu.
Przed kodem określ, czy wejście jest drzewem, DAG, czy dowolnym grafem.
Przy nieujemnych różnych wagach rozważ Dijkstrę: pierwsze odkrycie nie zamyka
tam dystansu. Przy ujemnych wagach trzeba wybrać inny algorytm i uzasadnienie.
Siatka jest sposobem reprezentacji sąsiadów i nie zmienia tej decyzji.

### DP czy greedy

Zaproponuj lokalną regułę i spróbuj zbudować małe wejście, na którym przegrywa.
Wybór największej monety zawodzi dla nominałów `[1,3,4]` i kwoty 6. Sam brak
znalezionego kontrprzykładu nie jest dowodem. Jeśli potrafisz zamienić pierwszy
wybór dowolnego optimum na swój bez pogorszenia, masz podstawę do greedy.

Jeżeli taki argument nie działa, poszukaj powtarzanych podproblemów. Dla DP
najpierw zapisz zdanie „dp[...] oznacza ...”, przypadek bazowy i zbiór ostatnich
decyzji. Dodaj wymiar, gdy dwie historie o tym samym stanie pozornym dają inne
możliwości kontynuacji. „Minimum” w treści nie rozstrzyga między DP i greedy.
Praktyczne przejście rekurencja → memoizacja → tabulacja → redukcja pamięci
pokazuje [osobna karta](patterns/25-dp-evolution.md), z porównaniem czterech wersji.

### Heap czy sortowanie

Gdy potrzebujesz całego porządku, sortowanie jest prostym punktem wyjścia.
Gdy wystarcza k najlepszych, kopiec o rozmiarze k może ograniczyć pamięć i koszt
aktualizacji strumienia. Gdy stale dopływa najlepszy kandydat z kilku uporządkowanych
źródeł, kopiec może reprezentować ich aktualne czoła. Zdefiniuj remisy i pamiętaj,
że iteracja po PriorityQueue nie zwraca elementów w posortowanej kolejności.

### Zwykłe szukanie czy binary search po odpowiedzi

Napisz funkcję logiczną `possible(candidate)` i spróbuj znaleźć dwie wartości,
które łamią jej monotoniczność. Np. jeśli ładunek da się przewieźć z pojemnością C,
to przy niezmienionej reszcie reguł da się też z większą pojemnością. Szukasz
najmniejszego C spełniającego warunek. Koszt sprawdzenia jest częścią kosztu
całego algorytmu. Wymóg „dokładnie” zamiast „co najwyżej” może zmienić argument.

## Trzy analizy bez implementacji

### Fragment logów o podanej sumie zmian

Treść opisuje spójny odcinek zmian salda, dopuszcza wartości ujemne i pyta o jego
liczbę. Brute force wybiera dwa końce i liczy sumę. Zamiast aktualizować całe
fragmenty, nazwij sumę prefiksu. Różnica prefiksów opisuje fragment; potrzebny
jest licznik wcześniejszych wartości, bo różne początki mogą mieć tę samą sumę.
Hipotezę sprawdź na zerach i fragmencie zaczynającym się na pozycji 0.
Zapisz niezmiennik mapy przed odczytem i przed dopisaniem bieżącego prefiksu.

### Kolejność modułów szkolenia

Treść mówi, że moduł może mieć wiele wymagań wstępnych i trzeba wykryć sprzeczność.
To nie lista z jednym następnikiem: fast/slow nie pasuje. Model to skierowany graf
z krawędzią od wymaganego modułu do odblokowywanego. Kandydatem jest topological
sort. Sprawdź osobny cykl obok poprawnej składowej oraz przypadek bez zależności.
Jeżeli dodatkowo pytanie wymaga najmniejszego numeru dostępnego modułu, zmienia
się wybór struktury gotowych elementów, a nie znaczenie zależności.

### Najlepsze wyniki napływające z pomiarów

Treść wymaga po każdej nowej wartości granicy k najlepszych, a historii nie można
przeglądać ponownie. Pełne sortowanie po każdej aktualizacji powtarza pracę.
Kandydatem jest kopiec rozmiaru k z najgorszym zachowanym wynikiem na szczycie.
Trzeba doprecyzować, co zwracać przed nadejściem k elementów oraz czy duplikaty
liczą się osobno. Te dwa warunki należą do kontraktu, nie do implementacyjnego detalu.

## Gdy nie widzisz rozwiązania

Zmniejsz przykład do 3–5 elementów i wypisz kandydatów. Zaznacz identyczne obliczenia
i informacje, które wystarczy zachować po jednym kroku. Spróbuj posortować dane
na kartce i sprawdź, co przez to zyskujesz oraz tracisz. Przy rekurencji narysuj
drzewo wywołań i zaznacz powracające stany. Przy optymalizacji usuń ostatnią decyzję
z gotowego rozwiązania i zapytaj, jaki mniejszy problem pozostaje.

Jeżeli nadal nie masz pomysłu, weź jedną wskazówkę dotyczącą własności, a nie gotowy
kod. Potem zapisz własne uzasadnienie i kontrprzykład dla odrzuconej techniki.
Nie poprawiaj szczegółów składni, dopóki nie wiesz, co reprezentuje stan.

## Co oddać po próbie

- Kontrakt i przykład z oczekiwanym wynikiem.
- Brute force oraz obserwację pozwalającą ograniczyć pracę.
- Niezmiennik albo stan i przejście DP; dla greedy argument poprawności wyboru.
- Kod, koszt czasu i pamięci z nazwanymi parametrami.
- Wyniki testów, nierozstrzygnięte ograniczenia i jeden wariant zmieniający technikę.

Przy pierwszym kontakcie z [bankiem zadań](exercises/README.md) kategoria jest
wskazówką. Później wybieraj zadania z różnych plików i najpierw analizuj samą treść.
Testy sprawdzają konkretne zachowania; nie potwierdzają deklarowanej złożoności
i nie zastępują uzasadnienia kompletności rozwiązania.
