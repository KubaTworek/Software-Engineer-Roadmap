# Algorytmy i struktury danych

[Spis rozdziałów](README.md) · [Książki i źródła](MATERIALY.md#15-algorytmy-i-struktury-danych)

Odpowiedzi łączą algorytm, niezmiennik, przejście na danych i wybrane implementacje rdzenia w Javie. n oznacza rozmiar wejścia, V/E liczbę wierzchołków/krawędzi. Koszt pamięci wyniku zaznaczono tam, gdzie ma znaczenie. Kod z pełną metodą można osadzić w klasie; fragmenty wykorzystujące pola istniejącej struktury są oznaczone jako szkice. Ogólne hasła „drzewa”, „grafy” czy „LeetCode Medium” z notatek nie definiują osobnego zadania; ich techniki występują poniżej.

<!-- question-toc:start -->
## Spis pytań

- [ALG-01. Jak uzyskać insert, delete i getRandom w średnim O(1)?](#alg-01-jak-uzyskać-insert-delete-i-getrandom-w-średnim-o1)
- [ALG-02. Jak scalać nakładające się przedziały?](#alg-02-jak-scalać-nakładające-się-przedziały)
- [ALG-03. Jak znaleźć najtańszy lot z najwyżej K przesiadkami?](#alg-03-jak-znaleźć-najtańszy-lot-z-najwyżej-k-przesiadkami)
- [ALG-04. Jak zbudować LRU i LFU cache z operacjami O(1)?](#alg-04-jak-zbudować-lru-i-lfu-cache-z-operacjami-o1)
- [ALG-05. Jak policzyć minimalną liczbę sal spotkań lub peronów przez min-heap?](#alg-05-jak-policzyć-minimalną-liczbę-sal-spotkań-lub-peronów-przez-min-heap)
- [ALG-06. Jak policzyć liczbę wysp?](#alg-06-jak-policzyć-liczbę-wysp)
- [ALG-07. Jak rozwiązać Word Ladder najkrótszą transformacją?](#alg-07-jak-rozwiązać-word-ladder-najkrótszą-transformacją)
- [ALG-08. Jak znaleźć K najczęstszych elementów?](#alg-08-jak-znaleźć-k-najczęstszych-elementów)
- [ALG-09. Jak wykryć cykl w grafie skierowanym i rozwiązać Course Schedule?](#alg-09-jak-wykryć-cykl-w-grafie-skierowanym-i-rozwiązać-course-schedule)
- [ALG-10. Jak utrzymywać medianę strumienia?](#alg-10-jak-utrzymywać-medianę-strumienia)
- [ALG-11. Jak scalić K posortowanych list?](#alg-11-jak-scalić-k-posortowanych-list)
- [ALG-12. Jak znaleźć maksymalną sumę spójnego podciągu przez algorytm Kadane'a?](#alg-12-jak-znaleźć-maksymalną-sumę-spójnego-podciągu-przez-algorytm-kadanea)
- [ALG-13. Jak znaleźć K-ty największy element strumienia, a drugi największy przez PriorityQueue?](#alg-13-jak-znaleźć-k-ty-największy-element-strumienia-a-drugi-największy-przez-priorityqueue)
- [ALG-14. Jak zaprojektować scheduler z priorytetami zadań?](#alg-14-jak-zaprojektować-scheduler-z-priorytetami-zadań)
- [ALG-15. Jak rozwiązywać Two Sum i warianty k-sum?](#alg-15-jak-rozwiązywać-two-sum-i-warianty-k-sum)
- [ALG-16. Na czym polega binary search on answer dla pojemności, dni lub wag?](#alg-16-na-czym-polega-binary-search-on-answer-dla-pojemności-dni-lub-wag)
- [ALG-17. Jak znaleźć najdłuższy rosnący podciąg przez DP i patience sorting?](#alg-17-jak-znaleźć-najdłuższy-rosnący-podciąg-przez-dp-i-patience-sorting)
- [ALG-18. Jak sklonować graf lub strukturę z losowymi wskaźnikami?](#alg-18-jak-sklonować-graf-lub-strukturę-z-losowymi-wskaźnikami)
- [ALG-19. Jak serializować i deserializować drzewo binarne?](#alg-19-jak-serializować-i-deserializować-drzewo-binarne)
- [ALG-20. Jak policzyć wodę zatrzymaną między słupkami?](#alg-20-jak-policzyć-wodę-zatrzymaną-między-słupkami)
- [ALG-21. Jak rozwiązać Word Break przez DP i trie?](#alg-21-jak-rozwiązać-word-break-przez-dp-i-trie)
- [ALG-22. Jak odwracać listę w grupach K elementów?](#alg-22-jak-odwracać-listę-w-grupach-k-elementów)
- [ALG-23. Jak znaleźć najdłuższy fragment tekstu bez powtarzających się znaków?](#alg-23-jak-znaleźć-najdłuższy-fragment-tekstu-bez-powtarzających-się-znaków)
- [ALG-24. Jak znaleźć najkrótszą podtablicę o sumie dokładnie równej target?](#alg-24-jak-znaleźć-najkrótszą-podtablicę-o-sumie-dokładnie-równej-target)
- [ALG-25. Jak odwrócić tekst bez gotowej metody i sprawdzić palindrom?](#alg-25-jak-odwrócić-tekst-bez-gotowej-metody-i-sprawdzić-palindrom)
- [ALG-26. Jak policzyć częstości znaków, znaleźć pierwszy niepowtarzalny lub duplikaty tablicy?](#alg-26-jak-policzyć-częstości-znaków-znaleźć-pierwszy-niepowtarzalny-lub-duplikaty-tablicy)
- [ALG-27. Jak wykryć cykl listy jednokierunkowej?](#alg-27-jak-wykryć-cykl-listy-jednokierunkowej)
- [ALG-28. Jak optymalizować dynamic programming, gdy pytanie nie podaje konkretnego zadania?](#alg-28-jak-optymalizować-dynamic-programming-gdy-pytanie-nie-podaje-konkretnego-zadania)
<!-- question-toc:end -->

## ALG-01. Jak uzyskać insert, delete i getRandom w średnim O(1)?

Łączę dynamiczną tablicę wartości z mapą wartość → indeks. Przy usunięciu zamieniam element z ostatnim, aktualizuję indeks przeniesionej wartości i skracam tablicę. Losuję równomiernie indeks z aktualnego zakresu, dzięki czemu każda wartość ma taką samą szansę.

Niezmiennik: mapa i tablica opisują ten sam zbiór oraz pozycje. Operacje są średnio/amortyzowanie O(1), pamięć O(n). Testuję usunięcie ostatniego elementu, duplikat insertu i pusty zbiór; multiset wymaga mapy zbiorów indeksów.

Dla zbioru [A,B,C] mapa wskazuje A→0, B→1, C→2. Usunięcie B przenosi C na indeks 1, aktualizuje mapę i usuwa koniec; nie przesuwamy wszystkich następnych elementów. Losowanie indeksu jest równomierne tylko wtedy, gdy tablica nie ma dziur ani duplikatów niezgodnych z kontraktem zbioru.

Rdzeń usuwania w szkicu klasy z polami values i index:

<!-- example:ALG-01-1 kind:java-sketch -->
**Kontekst:** Szkic Java — wymaga wskazanych pól lub metod otoczenia; poza automatyczną kompilacją.

~~~java
Integer position = index.remove(value);
if (position == null) return false;
int lastIndex = values.size() - 1;
var last = values.get(lastIndex);
if (position != lastIndex) {
    values.set(position, last);
    index.put(last, position);
}
values.remove(lastIndex);
return true;
~~~

Rozróżnienie ostatniego elementu zapobiega ponownemu wstawieniu właśnie usuniętego klucza do mapy.

## ALG-02. Jak scalać nakładające się przedziały?

Sortuję po początku i przechodzę liniowo, rozszerzając koniec bieżącego przedziału albo odkładając go do wyniku. Po każdym kroku wynik obejmuje dokładnie dotychczasowe wejście i nie zawiera scalanego dalej sąsiada.

Koszt O(n log n) czasu, wynik O(n); dodatkowa pamięć sortowania zależy od reprezentacji. Ustalam, czy stykające się końce się łączą: [1,2] i [2,3] różnią się od semantyki półotwartej [1,2), [2,3). Waliduję odwrócone granice i puste wejście.

Przykład dla przedziałów domkniętych [1,3], [2,6], [8,10] daje [1,6], [8,10]. Po sortowaniu żaden późniejszy początek nie będzie mniejszy, więc po rozdzieleniu dwóch przedziałów nie musimy wracać do zakończonego wyniku.

<!-- example:ALG-02-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static int[][] mergeClosed(int[][] intervals) {
    int[][] sorted = intervals.clone();
    java.util.Arrays.sort(sorted, java.util.Comparator.comparingInt(a -> a[0]));
    var result = new java.util.ArrayList<int[]>();
    for (int[] item : sorted) {
        if (result.isEmpty() || result.get(result.size() - 1)[1] < item[0]) {
            result.add(item.clone());
        } else {
            int[] last = result.get(result.size() - 1);
            last[1] = Math.max(last[1], item[1]);
        }
    }
    return result.toArray(int[][]::new);
}
~~~

Przykład zakłada poprawne pary start≤end i nie modyfikuje tablic wejściowych.

## ALG-03. Jak znaleźć najtańszy lot z najwyżej K przesiadkami?

K przesiadek oznacza najwyżej K+1 krawędzi. W wariancie Bellman–Ford wykonuję K+1 relaksacji, każda czyta kopię wyników poprzedniej rundy. Aktualizacja w miejscu mogłaby w jednej rundzie dopuścić zbyt wiele lotów.

Koszt O((K+1)E) czasu i O(V) pamięci przy dwóch tablicach. Alternatywna kolejka priorytetowa musi przechowywać także liczbę wykorzystanych krawędzi; sama najtańsza cena do miasta nie wystarczy. Testuję brak trasy, K=0 i tańszą trasę przekraczającą limit.

Kontrprzykład dla zwykłego „najtańsze do miasta”: dojście do X za 10 wykorzystuje już wszystkie dozwolone loty, a dojście za 15 zostawia jeden lot do celu. Odrzucenie droższego stanu zgubi poprawną odpowiedź. Stan musi uwzględniać limit kroków.

W Bellman–Ford poprzednia tablica reprezentuje „najwyżej i krawędzi”. Nowa zaczyna jako jej kopia, żeby zachować trasy krótsze i zawierać relaksacje o jeden lot dłuższe. Dla K=0 wykonuję jedną rundę, więc dopuszczam tylko lot bezpośredni. Kwoty dodaję w long i pomijam nieosiągalne źródła, żeby INF nie przepełniło obliczeń.

<!-- follow-up:ALG-03 -->
**Sprawdź rozumienie:** Najtańsza ścieżka do miasta zużyła wszystkie dozwolone loty. Dlaczego nie wolno odrzucić droższej z mniejszą liczbą lotów?

<details>
<summary>Kierunek odpowiedzi</summary>

Stan obejmuje koszt i wykorzystany budżet krawędzi. Droższy prefiks może być jedynym, który da się jeszcze przedłużyć do celu. Relaksacja z poprzedniej warstwy pilnuje limitu.

</details>

## ALG-04. Jak zbudować LRU i LFU cache z operacjami O(1)?

LRU łączy mapę klucz → węzeł z listą dwukierunkową kolejności użycia. Get/put przenosi węzeł na początek, a eviction usuwa ogon. LFU dodaje mapę klucz → częstotliwość, kubełki częstotliwości z kolejnością LRU i minFrequency.

Średni koszt operacji O(1), pamięć O(capacity), przy sprawnym utrzymaniu wszystkich indeksów. Dla LFU ustalam tie-breaker, starzenie i przepełnienie liczników. Testuję capacity=0, aktualizację klucza i eviction po get. TTL i wielowątkowość są dodatkowymi protokołami, nie własnościami samej listy.

Najkrótszy szkic LRU może wykorzystać access-order LinkedHashMap:

<!-- example:ALG-04-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static final class Lru<K, V> extends java.util.LinkedHashMap<K, V> {
    private final int capacity;
    Lru(int capacity) {
        super(16, 0.75f, true);
        if (capacity < 0) throw new IllegalArgumentException();
        this.capacity = capacity;
    }
    @Override
    protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
}
~~~

To jednowątkowy rdzeń bez TTL; get także zmienia kolejność. Dla LFU sekwencja put(A), put(B), get(A), put(C) usuwa B, bo A ma większą częstotliwość. Sam heap częstotliwości zwykle podnosi koszt aktualizacji do O(log n), więc nie realizuje żądanego O(1).

## ALG-05. Jak policzyć minimalną liczbę sal spotkań lub peronów przez min-heap?

Sortuję zdarzenia po początku, a heap przechowuje końce aktywnych przedziałów. Przed dodaniem usuwam zakończone, następnie zapisuję maksymalny rozmiar. Wariant przechowujący ostatni koniec każdej sali również działa przy konsekwentnej interpretacji heapu.

Koszt O(n log n) czasu i O(n) pamięci. Dla przedziałów półotwartych koniec równy początkowi zwalnia zasób; dla rozkładu pociągów może być potrzebny dodatkowy bufor. Testuję jednoczesne przyjazdy/odjazdy i zagnieżdżone spotkania.

Dla [0,10), [5,7), [7,9) potrzebne są dwie sale: zwolnienie o 7 pozwala użyć tej samej sali od 7. Przetwarzanie end≤start przed dodaniem nowego spotkania jest częścią kontraktu półotwartego.

<!-- example:ALG-05-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static int rooms(int[][] meetings) {
    int[][] sorted = meetings.clone();
    java.util.Arrays.sort(sorted, java.util.Comparator.comparingInt(a -> a[0]));
    var ends = new java.util.PriorityQueue<Integer>();
    int maximum = 0;
    for (int[] meeting : sorted) {
        while (!ends.isEmpty() && ends.peek() <= meeting[0]) ends.poll();
        ends.add(meeting[1]);
        maximum = Math.max(maximum, ends.size());
    }
    return maximum;
}
~~~

Kod zakłada dodatnią długość i poprawne pary. Dla pociągów dodatkowy czas obsługi należy doliczyć do końca.

## ALG-06. Jak policzyć liczbę wysp?

Przechodzę po siatce; każde nieodwiedzone pole lądu rozpoczyna BFS/DFS, który oznacza całą składową. Liczba rozpoczętych przeszukań jest wynikiem. Ustalam sąsiedztwo cztero- lub ośmiokierunkowe i czy wolno modyfikować wejście.

Czas O(rows × cols), pamięć w najgorszym razie O(rows × cols). Rekurencja może przepełnić stos dla długiej wyspy; iteracyjna kolejka/stos daje kontrolę. Testuję samą wodę, jedną długą wyspę i stykanie po przekątnej.

W siatce 110/010/001 są dwie wyspy przy czterech kierunkach, a jedna przy ośmiu, ponieważ ostatni ląd styka się po przekątnej. Ta mała zmiana specyfikacji zmienia poprawny wynik bez zmiany ogólnej techniki.

BFS oznacza pole przy włożeniu do kolejki, nie dopiero przy wyjęciu; inaczej wielu sąsiadów może dodać je wielokrotnie. Jeśli nie wolno zmieniać siatki, używam visited. Jeżeli wolno, zamiana lądu na wodę ogranicza dodatkową tablicę, ale nadal istnieje kolejka. Koszt pamięci nie staje się O(1) tylko dlatego, że nie użyto osobnego visited.

## ALG-07. Jak rozwiązać Word Ladder najkrótszą transformacją?

Słowa są wierzchołkami, a zmiana jednej litery dozwoloną krawędzią. BFS po warstwach znajduje najkrótszą ścieżkę w grafie nieważonym; bidirectional BFS może ograniczyć przeszukiwanie. Odwiedzenie oznaczam przy dodaniu, żeby nie mnożyć wpisów.

Dla N słów długości L generowanie zamian kosztuje około O(N × L × alfabet × koszt utworzenia/sprawdzenia słowa); w Javie tworzenie String często dodaje czynnik L. Indeks wzorców ma własny koszt pamięci. Ustalam, czy długość liczy słowa czy krawędzie, i brak słowa końcowego.

Dla hit → hot → dot → dog → cog wynik to cztery zmiany lub pięć słów, zależnie od definicji. BFS odwiedza warstwy od początku, więc pierwsze dotarcie do celu daje najkrótszą liczbę krawędzi. DFS bez dodatkowego wyszukiwania nie daje tej gwarancji.

Indeks wzorców typu h*t łączy słowa różniące się w danej pozycji. Przetworzony kubełek można świadomie usunąć/oznaczyć, aby nie skanować go wielokrotnie. Bidirectional BFS rozwija mniejszy frontier, ale musi poprawnie łączyć spotkane warstwy. Słowa różnej długości i niedozwolone znaki wymagają jawnej walidacji lub innego grafu.

## ALG-08. Jak znaleźć K najczęstszych elementów?

Najpierw zliczam częstotliwości w mapie. Heap minimum rozmiaru K zachowuje najlepszych kandydatów w O(n + u log K), gdzie u to liczba różnych wartości. Buckety według częstotliwości pozwalają uzyskać O(n) czasu kosztem O(n+u) pamięci.

Remisy wymagają jawnej reguły, jeżeli wynik ma być deterministyczny. Testuję K=0, K większe od u i wszystkie częstości równe. Dla nieograniczonego streamu dokładna mapa może nie mieścić się w pamięci; sketch zmienia kontrakt na przybliżony.

Dla [1,1,1,2,2,3] i K=2 wynik zawiera 1 oraz 2. Heap minimum trzyma najsłabszego z aktualnie najlepszych kandydatów na korzeniu. Nowy kandydat go zastępuje tylko, gdy jest lepszy według kompletnego komparatora, także dla remisów.

Bucket i zawiera wartości o częstości i, więc przejście od największej częstości nie potrzebuje pełnego sortowania wszystkich różnych elementów. Ma to jednak pamięć proporcjonalną do n i nie pasuje bez zmian do nieskończonego strumienia. Dla strumienia przybliżonego trzeba podać zakres błędu i sposób wyboru kandydatów.

## ALG-09. Jak wykryć cykl w grafie skierowanym i rozwiązać Course Schedule?

Algorytm Kahna zlicza indegree, usuwa wierzchołki o stopniu wejściowym zero i aktualizuje następców. Jeśli usunięto mniej niż V, pozostał cykl. Alternatywą jest DFS z trzema kolorami; krawędź do aktualnie odwiedzanego wierzchołka oznacza cykl.

Koszt O(V+E) czasu i pamięci. Dla Course Schedule kierunek krawędzi wynika z zależności prerequisite → kurs. Sam znacznik visited nie wystarcza w DFS skierowanym. Testuję self-loop, graf niespójny i kilka poprawnych porządków.

Przykład kursów 0→1, 1→2 pozwala zdjąć wszystkie wierzchołki; dodanie 2→0 zostawia kolejkę początkowo pustą mimo istniejących zadań. Rdzeń Kahna dla par prerequisite→next:

<!-- example:ALG-09-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static boolean acyclic(int n, int[][] edges) {
    var next = new java.util.ArrayList<java.util.List<Integer>>();
    for (int i = 0; i < n; i++) next.add(new java.util.ArrayList<>());
    int[] degree = new int[n];
    for (int[] edge : edges) { next.get(edge[0]).add(edge[1]); degree[edge[1]]++; }
    var ready = new java.util.ArrayDeque<Integer>();
    for (int i = 0; i < n; i++) if (degree[i] == 0) ready.add(i);
    int visited = 0;
    while (!ready.isEmpty()) {
        int node = ready.remove();
        visited++;
        for (int target : next.get(node)) if (--degree[target] == 0) ready.add(target);
    }
    return visited == n;
}
~~~

Zakładamy poprawne indeksy. Duplikaty krawędzi muszą być zliczane i zdejmowane spójnie.

## ALG-10. Jak utrzymywać medianę strumienia?

Dwa heap'y przechowują dolną i górną połowę: max-heap dolną, min-heap górną. Wszystkie elementy dolnej są nie większe od górnej, a rozmiary różnią się najwyżej o jeden. Po insert przywracam oba niezmienniki.

Insert kosztuje O(log n), mediana O(1), pamięć O(n). Dla parzystej liczby elementów obliczam średnią bez przepełnienia sumy typów całkowitych. Pusta kolekcja ma jawny kontrakt. Usuwanie elementów okna wymaga dodatkowych indeksów lub lazy deletion.

Po dodaniu 5, 2, 8 dolny heap zawiera np. [5,2], górny [8], więc mediana to 5. Po dodaniu 10 i wyrównaniu rozmiarów mediana wynosi (5+8)/2=6,5. Sam podział po stałym progu wartości nie utrzyma połówek dla dowolnego strumienia.

Średnią liczę przez konwersję do long/double przed dodaniem, nie po możliwym overflow int. Przy oknie przesuwanym usuwanie starego elementu może wymagać delayed deletion i kontroli rzeczywistych rozmiarów heapów. W przeciwnym razie zaległe elementy zniekształcą zarówno medianę, jak i balans.

## ALG-11. Jak scalić K posortowanych list?

Min-heap zawiera bieżący pierwszy element każdej niepustej listy. Usuwam minimum do wyniku i wkładam następnika z tej samej listy. Wszystkie pozostałe kandydatury są nie mniejsze od swoich aktualnych głów, więc minimum heapu jest globalnie poprawne.

Dla N elementów koszt O(N log K) i O(K) dodatkowej pamięci, poza wynikiem. Można też scalać parami w divide-and-conquer. Ustalam, czy wolno przepinać węzły wejścia, i testuję puste listy, remisy oraz K=0.

Dla list [1,4], [2,3], [0,8] heap zaczyna od 1,2,0. Po zdjęciu 0 dodaje 8 z tej samej listy, więc następne minimum to 1. Heap przechowuje maksymalnie K aktywnych głów, a nie wszystkie N elementów.

Jeżeli przepinam wejściowe next, caller traci pierwotny kształt list. To powinno być jawne; wariant kopiujący węzły kosztuje dodatkowo pamięć wyniku. Dla strumieni z I/O ważne są zakończenie i zamknięcie źródeł. Równe wartości potrzebują tie-breakera tylko wtedy, gdy kontrakt wymaga stabilnej kolejności źródeł.

## ALG-12. Jak znaleźć maksymalną sumę spójnego podciągu przez algorytm Kadane'a?

Dla każdej pozycji najlepszy fragment kończący się tutaj albo zaczyna się od bieżącej liczby, albo przedłuża poprzedni. Aktualizuję także globalne maksimum. Czas O(n), pamięć O(1); indeksy początku/końca wymagają kilku dodatkowych zmiennych.

Dla niepustego wyniku inicjalizuję pierwszym elementem, żeby tablica samych liczb ujemnych nie dała błędnego zera. Ustalam overflow i pusty input. Wariant kołowy wymaga również minimalnego fragmentu i osobnej obsługi przypadku wszystkich ujemnych.

Przykładowa implementacja dla niepustej tablicy:

<!-- example:ALG-12-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static long maxSubarray(int[] values) {
    if (values.length == 0) throw new IllegalArgumentException();
    long ending = values[0], best = values[0];
    for (int i = 1; i < values.length; i++) {
        ending = Math.max(values[i], ending + values[i]);
        best = Math.max(best, ending);
    }
    return best;
}
~~~

Dla [-3,-1,-2] odpowiedź to -1, nie 0. Niezmiennik ending oznacza najlepszy niepusty fragment kończący się dokładnie tutaj. Gdy poprzedni wynik jest ujemny, jego dołączenie tylko pogarsza start z bieżącej wartości. Rekonstrukcja zapamiętuje początek nowego fragmentu i najlepsze granice.

## ALG-13. Jak znaleźć K-ty największy element strumienia, a drugi największy przez PriorityQueue?

Min-heap rozmiaru K zachowuje K największych dotychczasowych wartości; korzeń jest K-tą, gdy zebrano co najmniej K elementów. Nowy element większy od minimum zastępuje korzeń. Koszt O(log K) na element i O(K) pamięci.

Dla drugiego największego ustawiam K=2. Jeśli chodzi o drugą różną wartość, duplikaty trzeba wykluczyć; odpowiedź dla [5,5,4] zależy od definicji. Dla stałego K=2 można użyć dwóch zmiennych w O(1), ale pytanie może wymagać konkretnej struktury.

Przykład streamu 4,5,8,2 dla K=3 utrzymuje heap [4,5,8]; korzeń 4 jest trzecią największą wartością. Dodanie 10 usuwa 4 i daje wynik 5. Poniżej K elementów nie ma jeszcze pełnej odpowiedzi.

<!-- example:ALG-13-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static void keepLargest(java.util.PriorityQueue<Integer> top, int k, int value) {
    if (k <= 0) throw new IllegalArgumentException();
    top.add(value);
    if (top.size() > k) top.poll();
}
~~~

Metoda zakłada min-heap oraz stan z najwyżej K elementami. Dla „drugiej różnej” sam heap z duplikatami nie spełnia kontraktu; trzeba kontrolować różne wartości albo użyć dwóch zmiennych.

## ALG-14. Jak zaprojektować scheduler z priorytetami zadań?

Osobno utrzymuję kolejność gotowości w czasie i priorytet zadań gotowych. Min-heap deadline pozwala aktywować zadania, a priority queue wybiera następne według priorytetu z sekwencją jako tie-breakerem. Jedna kolejka sortowana wyłącznie po priorytecie może blokować gotowe zadanie przyszłym zadaniem o wyższym priorytecie.

Dodanie/pobranie zwykle O(log n), pamięć O(n). Ustalam starvation/aging, anulowanie i aktualizację priorytetu; heap JDK nie ma automatycznej aktualizacji po zmianie pola. Współbieżność i trwałość są osobnymi wymaganiami.

Zadanie wysokiego priorytetu zaplanowane za godzinę nie może blokować zadania niskiego priorytetu gotowego teraz. Dlatego najpierw kwalifikuję readyAt≤now, potem wybieram priorytet. Zmiana priorytetu obiektu znajdującego się w PriorityQueue nie przebuduje automatycznie heapu.

Przy anulowaniu można stosować lazy deletion z wersją, ale stare rekordy trzeba sprzątać, aby pamięć nie rosła bez końca. Dla kolejności remisów używam rosnącej sekwencji, nie niejednoznacznego czasu milisekundowego. Test kontroluje zegar i sprawdza gotowość, kolejność, starvation oraz anulowane wpisy, bez czekania rzeczywistych minut.

## ALG-15. Jak rozwiązywać Two Sum i warianty k-sum?

Two Sum w nieposortowanej tablicy sprawdza w mapie dopełnienie przed dodaniem bieżącej wartości: O(n) średnio czasu i O(n) pamięci. Dla tablicy posortowanej dwa wskaźniki dają O(n) czasu i O(1) pamięci. Nie wolno użyć tej samej pozycji dwukrotnie.

W k-sum sortuję, wybieram część elementów rekurencyjnie i redukuję do two pointers, eliminując duplikaty; typowy koszt to O(n^(k-1)) dla stałego k, poza wynikiem. Ustalam, czy zwracam indeksy, wartości czy wszystkie kombinacje, i chronię sumy przed overflow.

Rdzeń Two Sum zwracający indeksy dla sumy wyrażonej jako long:

<!-- example:ALG-15-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static int[] twoSum(int[] values, long target) {
    var previous = new java.util.HashMap<Long, Integer>();
    for (int i = 0; i < values.length; i++) {
        Integer other = previous.get(target - values[i]);
        if (other != null) return new int[]{other, i};
        previous.put((long) values[i], i);
    }
    return new int[0];
}
~~~

Dla [3,3] i target=6 wynik korzysta z dwóch pozycji. Gdyby najpierw wstawić bieżący element, wyszukiwanie mogłoby wskazać ten sam indeks. Kontrakt powinien rozstrzygnąć, czy brak wyniku oznacza pustą tablicę, Optional czy wyjątek.

## ALG-16. Na czym polega binary search on answer dla pojemności, dni lub wag?

Definiuję monotoniczny predykat, np. „czy ta pojemność wystarczy do przewiezienia paczek w D dni”. Jeżeli działa dla C, musi działać dla większego C. Binarnie szukam pierwszej wartości spełniającej warunek, a greedy check symuluje podział bez zmiany kolejności paczek.

Koszt O(check × log zakresu), często O(n log zakresu), pamięć O(1). Dolna granica uwzględnia najcięższą paczkę, górna sumę. Bez dowodu monotoniczności binary search jest nieuzasadnione; uważam też na przepełnienie midpoint i sum.

Dla paczek [3,2,2,4,1] i D=3 pojemność 4 wymaga czterech dni: [3], [2,2], [4], [1], więc nie wystarcza. Pojemność 5 pozwala na [3,2], [2], [4,1], więc spełnia warunek.

Taki przykład pokazuje znaczenie dokładnego greedy check. Binary search utrzymuje przedział zawierający pierwszą wykonalną wartość; po odpowiedzi true zmniejsza górną granicę, po false dolną. Dowód greedy wynika z wypełniania dnia maksymalnym możliwym prefiksem bez zmiany kolejności paczek.

## ALG-17. Jak znaleźć najdłuższy rosnący podciąg przez DP i patience sorting?

DP liczy najlepszy wynik kończący się na pozycji w O(n²). Szybsza metoda utrzymuje tails: najmniejszy możliwy koniec rosnącego podciągu każdej długości. Binary search wskazuje miejsce podmiany; mniejszy koniec zwiększa szansę przedłużenia, nie zmniejsza uzyskanej długości.

Koszt O(n log n) i O(n) pamięci. Tails nie jest automatycznie faktycznym podciągiem; rekonstrukcja potrzebuje poprzedników i indeksów. Strictly increasing i non-decreasing wymagają innego traktowania równości w wyszukiwaniu.

Dla [3,1,2,5,4] tails zmienia się tak: [3] → [1] → [1,2] → [1,2,5] → [1,2,4]. Długość wynosi 3, lecz same końce są reprezentantami najlepszych możliwości.

<!-- example:ALG-17-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static int lisLength(int[] values) {
    int[] tails = new int[values.length];
    int size = 0;
    for (int value : values) {
        int left = 0, right = size;
        while (left < right) {
            int middle = left + (right - left) / 2;
            if (tails[middle] < value) left = middle + 1;
            else right = middle;
        }
        tails[left] = value;
        if (left == size) size++;
    }
    return size;
}
~~~

Lower bound zachowuje ścisły wzrost; dla non-decreasing trzeba inaczej traktować równość.

## ALG-18. Jak sklonować graf lub strukturę z losowymi wskaźnikami?

Mapa oryginalna tożsamość → kopia powstaje przy pierwszym odwiedzeniu, przed zejściem do sąsiadów. BFS/DFS kopiuje krawędzie do już utworzonych kopii; obsługuje cykle, self-loop i współdzielone węzły. Nie mapuję wyłącznie po wartości, bo dwa węzły mogą mieć tę samą etykietę.

Dla grafu koszt O(V+E) czasu i pamięci łącznie z kopią. Wariant listy next/random może mieć rozwiązanie przez przeplatanie kopii w O(1) dodatkowej pamięci, jeśli wolno chwilowo zmieniać wejście. Najpierw ustalam, czy pytanie dotyczy grafu czy listy.

Kopia musi zachować współdzielenie: jeśli dwa oryginalne węzły wskazują ten sam trzeci, ich kopie też powinny wskazywać jedną kopię trzeciego. Naiwne tworzenie nowego obiektu przy każdej krawędzi zmieni graf i może nie skończyć się na cyklu.

Mapa powinna bazować na tożsamości, jeśli equals węzła nie definiuje rzeczywistej identyfikacji; IdentityHashMap bywa właściwym narzędziem. Nowy węzeł rejestruję przed rekursją. Test porównuje strukturę krawędzi, brak współdzielenia mutowalnych obiektów między oryginałem i kopią oraz zachowanie self-loop, a nie tylko listę etykiet.

## ALG-19. Jak serializować i deserializować drzewo binarne?

Preorder z markerami null odtwarza kształt oraz wartości; bez markerów różne drzewa mogą mieć ten sam zapis. Można użyć też BFS z jawną konwencją brakujących dzieci. Parser konsumuje dokładnie oczekiwane tokeny i odrzuca nadmiar lub ucięty format.

Czas i zapis O(n), stos/queue do O(n) w najgorszym razie. Ustalam separator/escaping, typ wartości i wersję formatu. Ograniczam rozmiar i głębokość niezaufanego wejścia; długa jednostronna gałąź może przepełnić rekursję.

Drzewo z korzeniem 1 i lewym dzieckiem 2 można zapisać preorder jako 1,2,#,#,#. Bez markerów null zapis 1,2 nie rozróżnia lewego i prawego dziecka. Parser powinien odrzucić zarówno brakujące markery, jak i niezużyte końcowe tokeny.

Dla wartości tekstowych potrzebny jest format z escapingiem albo długością pola; zwykłe split po przecinku nie wystarczy, jeśli przecinek należy do danych. Wersjonuję format i ograniczam liczbę węzłów/głębokość. Weryfikacja round-trip obejmuje puste drzewo, duplikaty wartości i silnie niezbalansowane wejście.

## ALG-20. Jak policzyć wodę zatrzymaną między słupkami?

Dwa wskaźniki utrzymują maksima lewe i prawe. Rozstrzygam stronę z niższą bieżącą granicą, bo przeciwna zapewnia dostatecznie wysokie ograniczenie, i dodaję różnicę do jej maksimum. Czas O(n), dodatkowa pamięć O(1).

Alternatywa ze stosem monotonicznym rozlicza niecki w O(n) czasu i O(n) pamięci. Testuję ciąg rosnący, płaski, pusty i szeroką nieckę. Wysokości są nieujemne, a całkowita objętość może wymagać long.

Dla [0,2,0,3] nad trzecim polem mieści się 2 jednostki; ograniczeniem jest niższa z maksymalnych granic. Wariant dwóch wskaźników utrzymujący rozstrzygającą stronę:

<!-- example:ALG-20-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static long trapped(int[] height) {
    int left = 0, right = height.length - 1, leftMax = 0, rightMax = 0;
    long water = 0;
    while (left <= right) {
        if (leftMax <= rightMax) {
            leftMax = Math.max(leftMax, height[left]);
            water += leftMax - height[left++];
        } else {
            rightMax = Math.max(rightMax, height[right]);
            water += rightMax - height[right--];
        }
    }
    return water;
}
~~~

Kod zakłada nieujemne wysokości. Long chroni sumę wielu szerokich niecek, a puste wejście kończy pętlę od razu.

## ALG-21. Jak rozwiązać Word Break przez DP i trie?

`dp[i]` oznacza, że prefiks kończący się przed i da się podzielić na słowa. Od osiągalnej pozycji przechodzę po kolejnych znakach w trie; koniec słowa zaznacza następny osiągalny prefiks. To problem osiągalności, a nie zachłanny wybór najdłuższego słowa.

Dla maksymalnej długości słowa L koszt przejść O(nL), budowa trie proporcjonalna do sumy długości słów, pamięć DP O(n) plus słownik. Uważam na koszt substring w wariancie z setem. Zwrócenie wszystkich podziałów może mieć wykładniczy rozmiar.

Dla tekstu leetcode i słownika [leet, code] dp[0]=true, znalezienie leet ustawia dp[4], a przejście od 4 przez code ustawia dp[8]. Wybór najdłuższego słowa na każdej pozycji nie jest ogólnie poprawny: może zamknąć drogę do poprawnego dalszego podziału.

DP bez pamięci o poprzednikach odpowie tylko tak/nie. Jedno rozwiązanie odtwarzam przez zapis wybranej poprzedniej pozycji, wszystkie rozwiązania mogą być wykładniczo liczne. Trie ogranicza tworzenie substring, ale ma koszt węzłów i alfabetu. W testach rozróżniam pusty tekst, pusty słownik i słowa będące prefiksami innych.

## ALG-22. Jak odwracać listę w grupach K elementów?

Używam wartownika, znajduję koniec pełnej grupy, odwracam dokładnie K wskaźników i łączę ją z poprzednią oraz następną częścią. Zapamiętuję następnik przed przepisaniem next, żeby nie zgubić reszty listy.

Czas O(n), pamięć O(1) iteracyjnie. Ustalam, czy ostatnia niepełna grupa zostaje bez zmian — to typowa, ale nie jedyna specyfikacja. Testuję K=1, K>n, pustą listę oraz ochronę przed K≤0. Współdzielona mutacja wymaga odrębnego kontraktu.

Dla 1→2→3→4→5 i K=2 wynik standardowego wariantu to 2→1→4→3→5. Ostatnia pojedyncza pozycja zostaje. Wartownik usuwa specjalny przypadek wymiany głowy, ale trzeba zachować starą głowę grupy, bo po odwróceniu stanie się jej ogonem.

Najpierw sprawdzam, czy pełna grupa istnieje; odwracanie przed tym sprawdzeniem utrudnia zostawienie niepełnego końca bez zmian. Test wykrywa zgubiony fragment przez sprawdzenie liczby węzłów i braku cyklu, nie tylko pierwszych kilku wartości. Wariant rekurencyjny może mieć prostszy zapis, ale dodatkowy stos zależy od liczby grup.

## ALG-23. Jak znaleźć najdłuższy fragment tekstu bez powtarzających się znaków?

Sliding window utrzymuje początek i mapę ostatnich pozycji znaków. Po powtórzeniu przesuwam lewą granicę tylko w przód: do maksimum dotychczasowej granicy i ostatniej pozycji plus jeden. Każda pozycja jest przetwarzana stałą liczbę razy.

Koszt O(n) czasu i O(alfabetu) pamięci. Dla Javy ustalam jednostkę: char, Unicode code point czy grapheme cluster. Zwykła tablica 256 nie obsługuje całego Unicode. Testuję `abba`, pusty tekst i znaki spoza BMP.

Dla abba po drugim b lewa granica przechodzi na 2. Przy końcowym a nie może wrócić na 1; dlatego używamy maksimum, a nie samego last+1.

<!-- example:ALG-23-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static int longestUnique(String text) {
    int[] points = text.codePoints().toArray();
    var last = new java.util.HashMap<Integer, Integer>();
    int left = 0, best = 0;
    for (int right = 0; right < points.length; right++) {
        Integer previous = last.put(points[right], right);
        if (previous != null) left = Math.max(left, previous + 1);
        best = Math.max(best, right - left + 1);
    }
    return best;
}
~~~

Przykład liczy code points i alokuje ich tablicę O(n); nie rozwiązuje segmentacji grapheme clusters.

## ALG-24. Jak znaleźć najkrótszą podtablicę o sumie dokładnie równej target?

Dla dowolnych liczb używam prefix sums i mapy suma → najnowszy indeks. Dla bieżącej sumy S szukam S−target; najnowszy indeks daje najkrótszy fragment kończący się tutaj. Szukam przed aktualizacją mapy, aby nie uznać pustego fragmentu przy target=0.

Średnio O(n) czasu i O(n) pamięci. Sliding window jest poprawne tylko przy odpowiednich ograniczeniach znaków liczb i wymaga odróżnienia „równa target” od „co najmniej target”. Testuję liczby ujemne, zera, brak wyniku i przepełnienie sum.

Dla [1,-1,5,-2,3] i target=3 najkrótszy fragment to ostatnie [3]. Prefix sum pozwala obsłużyć liczby ujemne, dla których typowe przesuwanie okna według za dużej/małej sumy nie jest monotoniczne.

<!-- example:ALG-24-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static int shortestExact(int[] values, long target) {
    var latest = new java.util.HashMap<Long, Integer>();
    latest.put(0L, -1);
    long sum = 0;
    int best = Integer.MAX_VALUE;
    for (int i = 0; i < values.length; i++) {
        sum += values[i];
        Integer before = latest.get(sum - target);
        if (before != null) best = Math.min(best, i - before);
        latest.put(sum, i);
    }
    return best == Integer.MAX_VALUE ? -1 : best;
}
~~~

Przed dodaniem bieżącego indeksu sprawdzamy wynik, aby nie dopuścić pustego fragmentu.

<!-- follow-up:ALG-24 -->
**Sprawdź rozumienie:** Dlaczego do najkrótszej podtablicy o dokładnej sumie przechowujemy najnowszy indeks prefiksu?

<details>
<summary>Kierunek odpowiedzi</summary>

Dla tego samego końca i sumy nowszy początek daje krótszy niepusty fragment. Dla najdłuższej wersji zadania wybór indeksu byłby inny. Odczyt przed aktualizacją mapy chroni target=0 przed pustym wynikiem.

</details>

## ALG-25. Jak odwrócić tekst bez gotowej metody i sprawdzić palindrom?

Dwa wskaźniki zamieniają elementy od końców do środka albo porównują je dla palindromu. Czas O(n); odwrócenie niemutowalnego String wymaga bufora O(n). Odwracanie samych char może rozdzielić parę surogatów, więc definiuję jednostkę Unicode.

Dla tekstu użytkownika ustalam normalizację, wielkość liter, białe znaki i znaki łączące; code point też nie zawsze jest pełnym znakiem widocznym. Testy obejmują pusty tekst, parzystą/nieparzystą długość, emoji i akcent złożony z kilku punktów.

Dla ASCII dwa wskaźniki po char są wystarczające. Dla emoji z parą surogatów odwrócenie samych jednostek UTF-16 może uszkodzić zapis; code point rozwiązuje ten przypadek, lecz nie cały złożony znak widoczny, np. sekwencję z łączącym akcentem.

Przed implementacją palindromu ustalam normalizację, case folding i ignorowane znaki. „Ala” może być palindromem po normalizacji, ale nie przy surowym porównaniu. Test wprost dokumentuje wybraną semantykę. Brak gotowej reverse nie oznacza zakazu użycia API codePoints, chyba że wymaganie zadania mówi inaczej.

## ALG-26. Jak policzyć częstości znaków, znaleźć pierwszy niepowtarzalny lub duplikaty tablicy?

Mapa częstości daje średnio O(n) czasu i O(u) pamięci. Pierwszy unikalny znak znajduję drugim przejściem w kolejności wejścia, nie przez dowolny porządek HashMap. Dla duplikatów tablicy set visited i set wyniku odróżniają pierwsze wystąpienie od powtórzenia.

Ustalam, czy duplikat ma być wypisany raz czy za każde powtórzenie i czy liczę char/code points. Przy małym znanym alfabecie wystarczy tablica liczników. Sortowanie daje alternatywę O(n log n), jeśli można zmienić wejście i ważna jest mniejsza pamięć pomocnicza.

Dla swiss częstości to s=3, w=1, i=1; pierwszy niepowtarzalny to w, ponieważ wynik zależy od kolejności oryginalnego tekstu. Iteracja po HashMap może zwrócić i i być błędna mimo poprawnych liczników.

Dla duplikatów [2,1,2,2] ustalam, czy odpowiedzią jest [2] czy dwa powtórzenia 2. HashSet wykrywa fakt wcześniejszej obecności, licznik umożliwia szerszy kontrakt. Jeżeli alfabet jest mały i z góry znany, tablica zmniejsza narzut, lecz dla Unicode powinna odpowiadać wybranej jednostce znaków. Analizuję także kolejność wyniku i koszt pamięci.

## ALG-27. Jak wykryć cykl listy jednokierunkowej?

Algorytm Floyda przesuwa slow o jeden i fast o dwa węzły. W cyklu różnica pozycji zmienia się modulo jego długości, więc wskaźniki się spotkają. Gdy fast dochodzi do null, cyklu nie ma.

Czas O(n), pamięć O(1). Do znalezienia początku po spotkaniu ustawiam jeden wskaźnik na głowie i poruszam oba o jeden. Porównuję tożsamość węzłów, nie ich wartości. Testuję self-loop, cykl zaczynający się na głowie i pustą listę.

Jeśli cykl ma długość c, szybszy wskaźnik zbliża się względem wolniejszego o jeden modulo c, więc w końcu się spotkają. Po spotkaniu odległości pozwalają przejść dwoma wskaźnikami po jednym do początku cyklu.

<!-- example:ALG-27-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static final class Link {
    Link next;
}
static boolean hasCycle(Link head) {
    Link slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) return true;
    }
    return false;
}
~~~

Nie porównujemy wartości węzłów. Jednowątkowy algorytm zakłada, że struktura nie zmienia się równocześnie podczas przechodzenia.

## ALG-28. Jak optymalizować dynamic programming, gdy pytanie nie podaje konkretnego zadania?

Najpierw definiuję stan, przejście, przypadki bazowe i kolejność zależności. Potem ograniczam powtarzane obliczenia i zachowuję tylko potrzebne warstwy. Np. w plecaku 0/1 tablica 1D jest aktualizowana malejąco po pojemności, żeby nie użyć tego samego przedmiotu wielokrotnie.

Nie da się podać poprawnej złożoności „zadania DP” bez specyfikacji. Optymalizacja pamięci może utrudnić rekonstrukcję wyniku. Weryfikuję ją na małych przypadkach porównaniem z prostą metodą i kontrprzykładem dla nieprawidłowej kolejności aktualizacji.

Przykład plecaka 0/1: jeden przedmiot o wadze 2 i wartości 5 nie może wypełnić pojemności 4 wartością 10. Aktualizacja jednowymiarowej tablicy rosnąco pozwoliłaby użyć właśnie zapisanego wyniku i policzyć przedmiot dwa razy; malejąco zachowuje poprzednią warstwę.

Optymalizacja zaczyna się od znaczenia dp[i], nie od usunięcia tablicy. Sprawdzam zależności między warstwami i możliwość odtworzenia rozwiązania. Dla małych wejść metoda brute force jest dobrym oracle, a property „wynik nie maleje przy większym budżecie” wychwytuje część błędów, choć nie dowodzi pełnej poprawności.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [Cache w pamięci](../low-level-design/in-memory-cache/README.md)
- [Kalendarz zasobów](../low-level-design/resource-calendar/README.md)
- [Silnik promocji](../low-level-design/promotion-engine/README.md)
<!-- labs:end -->
