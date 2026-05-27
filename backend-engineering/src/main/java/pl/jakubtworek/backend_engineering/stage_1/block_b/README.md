# Wydajność aplikacji Java — teoria, diagnostyka i praktyczne zasady optymalizacji

## Podsumowanie wykonawcze

Wydajność aplikacji Java zależy przede wszystkim od poprawnej diagnozy problemu, a dopiero później od doboru konkretnych flag JVM, kolektora GC, struktur danych czy technik optymalizacji kodu. Najważniejsza zasada brzmi: należy profilować, a nie zgadywać. Bez pomiarów bardzo łatwo zoptymalizować fragment, który nie ma istotnego wpływu na działanie systemu, pogorszyć czytelność kodu albo wprowadzić błąd w imię pozornej poprawy wydajności.

W praktyce wydajność aplikacji jest wynikiem współdziałania wielu warstw: algorytmów, struktur danych, modelu pamięci, sposobu alokacji obiektów, działania garbage collectora, kompilacji JIT, konfiguracji JVM, liczby wątków, blokad, operacji I/O, bazy danych, sieci oraz środowiska uruchomieniowego. Zbyt częste skupianie się na mikrooptymalizacjach w kodzie źródłowym bywa błędem. Zmiana algorytmu z kwadratowego na liniowy lub liniowo-logarytmiczny zwykle daje większy efekt niż ręczne przepisywanie drobnych fragmentów kodu w nadziei, że będą „szybsze”.

Domyślne ustawienia współczesnego HotSpota są rozsądnym punktem startowym dla większości aplikacji. Tiered compilation, domyślny garbage collector i adaptacyjne mechanizmy JVM rozwiązują wiele problemów bez ręcznej ingerencji. Nie oznacza to jednak, że konfiguracja JVM jest nieważna. W środowisku produkcyjnym warto świadomie ustawić rozmiar sterty, włączyć logowanie GC, zbierać metryki, obserwować opóźnienia i mieć możliwość analizy profili CPU oraz alokacji. Optymalizacja powinna być procesem opartym na danych, a nie jednorazowym zestawem „magicznych flag”.

## Najważniejsza zasada: profiluj, nie zgaduj

Najczęstszy błąd w optymalizacji polega na rozpoczynaniu od zmian w kodzie lub konfiguracji bez wcześniejszego ustalenia, gdzie naprawdę znajduje się wąskie gardło. Aplikacja może być wolna z wielu powodów. Może zużywać cały CPU, może spędzać większość czasu na oczekiwaniu na bazę danych, może być blokowana przez synchronizację, może generować nadmierną liczbę obiektów i wywoływać częste GC, może mieć zbyt małą lub zbyt dużą pulę wątków, albo może cierpieć z powodu nieefektywnego algorytmu.

Profilowanie pozwala odpowiedzieć na pytanie, co aplikacja faktycznie robi w czasie działania. JFR, JMC, async-profiler, JMH, logi GC, metryki systemowe i flamegraphy służą różnym celom. JFR i JMC dobrze nadają się do ogólnej diagnostyki aplikacji działającej na JVM, ponieważ pozwalają analizować CPU, alokacje, blokady, GC, wyjątki, operacje I/O i zachowanie wątków. async-profiler jest szczególnie użyteczny do tworzenia flamegraphów CPU i alokacji, zwłaszcza gdy chcemy zobaczyć, które ścieżki wykonania realnie dominują. JMH jest narzędziem do mikrobenchmarków, ale powinien być używany ostrożnie, ponieważ mikrobenchmark nie zawsze reprezentuje zachowanie systemu produkcyjnego.

Dobra diagnoza zaczyna się od klasyfikacji problemu. Najpierw należy ustalić, czy aplikacja jest ograniczona przez CPU, pamięć, GC, blokady, I/O czy zewnętrzne zależności. Jeżeli czas odpowiedzi rośnie, ale CPU jest niski, problem może leżeć w oczekiwaniu na bazę, sieć, kolejkę, locki albo zbyt małą pulę wątków. Jeżeli CPU jest wysoki, trzeba sprawdzić profil CPU i algorytmy. Jeżeli występują skoki opóźnień, warto przeanalizować GC, safepointy, długie sekcje krytyczne i blokujące operacje. Bez takiego rozróżnienia optymalizacja jest strzelaniem w ciemno.

## Wydajność jako własność całego systemu

Wydajność aplikacji Java rzadko zależy wyłącznie od kodu Javy. Bardzo często główne problemy znajdują się poza JVM: w bazie danych, zapytaniach SQL, indeksach, warstwie sieciowej, konfiguracji kontenerów, limitach CPU, dysku, usługach zewnętrznych lub architekturze komunikacji. Dlatego nie należy zakładać, że każda degradacja wydajności wymaga strojenia garbage collectora albo zmiany flag JVM.

W systemach produkcyjnych szczególnie ważna jest obserwowalność. Sama średnia latencja jest niewystarczająca, ponieważ ukrywa problemy ogonowe. Aplikacja może mieć akceptowalny średni czas odpowiedzi, a jednocześnie fatalne percentyle p95, p99 lub p999. Użytkownik nie doświadcza średniej, tylko konkretnego opóźnienia konkretnego żądania. Wydajność należy więc opisywać przez throughput, latencję, percentyle, wykorzystanie CPU, zużycie pamięci, częstotliwość GC, czas pauz, liczbę wątków, liczbę połączeń, rozmiary kolejek oraz błędy timeoutów.

Optymalizacja powinna mieć jasno określony cel. Inaczej optymalizuje się system batchowy, który ma przetworzyć jak najwięcej danych w godzinę, a inaczej API użytkownika, które musi utrzymać niską latencję. Inaczej stroi się usługę z dużą liczbą krótkich żądań, a inaczej proces analityczny przetwarzający duże obiekty. Bez określenia celu łatwo poprawić jeden parametr kosztem drugiego, na przykład zwiększyć throughput, ale pogorszyć opóźnienia ogonowe.

## HotSpot, JIT i rozgrzewanie aplikacji

Java jest językiem kompilowanym do bytecode’u, który jest wykonywany przez JVM. Współczesny HotSpot nie wykonuje jednak kodu wyłącznie przez prostą interpretację. JVM obserwuje działanie programu, wykrywa gorące ścieżki i kompiluje je do kodu maszynowego przy użyciu JIT, czyli just-in-time compiler. Dzięki temu Java może osiągać bardzo wysoką wydajność w długotrwale działających procesach.

Tiered compilation łączy różne poziomy kompilacji i profilowania. Na początku kod może być interpretowany lub kompilowany mniej agresywnie, a po zebraniu danych profilowych JVM może wygenerować bardziej zoptymalizowaną wersję. To oznacza, że wydajność aplikacji po starcie może różnić się od wydajności po rozgrzaniu. Z tego powodu benchmark wykonywany przez kilka milisekund zaraz po uruchomieniu JVM jest zwykle bezwartościowy.

JIT wykonuje wiele optymalizacji, takich jak inline’owanie metod, eliminacja niepotrzebnych alokacji, escape analysis, usuwanie martwego kodu, devirtualizacja czy optymalizacje pętli. Ręczne mikrooptymalizacje mogą przeszkadzać JVM, jeżeli zaciemniają kod i utrudniają optymalizację. Czasami prosty, idiomatyczny kod jest szybszy niż kod „sprytny”, ponieważ JVM lepiej rozpoznaje jego strukturę.

Ważnym zjawiskiem jest deoptymalizacja. JVM może skompilować kod przy określonych założeniach, a potem je wycofać, jeśli rzeczywistość programu się zmieni. Przykładowo wywołanie wirtualne może być przez pewien czas monomorficzne, a potem stać się polimorficzne. Dlatego wydajność nie zawsze jest stabilna w czasie, szczególnie w aplikacjach o zmiennym profilu obciążenia.

## Sterta, stos i model pamięci JVM

Pamięć aplikacji Java obejmuje kilka obszarów. Sterta przechowuje obiekty zarządzane przez garbage collector. Stosy wątków przechowują ramki wywołań metod, zmienne lokalne i informacje potrzebne do wykonania kodu. Metaspace przechowuje metadane klas. JVM korzysta również z pamięci natywnej, między innymi dla struktur wewnętrznych, buforów bezpośrednich, stosów wątków i bibliotek natywnych.

Błędem jest utożsamianie całego zużycia pamięci procesu z rozmiarem sterty. Proces Java może zużywać istotnie więcej pamięci niż wynika z `-Xmx`, ponieważ `-Xmx` ogranicza maksymalną stertę, ale nie całą pamięć procesu. W środowiskach kontenerowych ma to duże znaczenie. Zbyt agresywne ustawienie sterty może zostawić za mało miejsca na pamięć natywną, stosy wątków, bufory i metadane, co prowadzi do problemów mimo pozornie poprawnej konfiguracji heapu.

Ustawienie `-Xms` i `-Xmx` na tę samą wartość bywa dobrym wyborem w produkcji, ponieważ ogranicza koszt dynamicznego powiększania i zmniejszania sterty oraz stabilizuje zachowanie aplikacji. Nie jest to jednak uniwersalne prawo. W środowiskach o zmiennym obciążeniu lub ograniczonych zasobach czasem celowo pozostawia się JVM możliwość adaptacji. Decyzja powinna zależeć od profilu aplikacji, wymagań latencji, dostępnej pamięci i sposobu uruchomienia.

## Garbage Collection jako zarządzanie kosztem alokacji

Garbage collector nie jest magicznym mechanizmem „za darmo”. Jego zadaniem jest odzyskiwanie pamięci po obiektach, które nie są już osiągalne, ale robi to kosztem CPU, pauz, przepustowości i złożoności działania. Wydajność GC zależy od tempa alokacji, czasu życia obiektów, rozmiaru sterty, liczby rdzeni, rodzaju kolektora i charakterystyki aplikacji.

W typowej aplikacji Java bardzo wiele obiektów żyje krótko. Generacyjne kolektory wykorzystują tę obserwację, dzieląc pamięć na obszary dla młodych i starszych obiektów. Młode obiekty są zbierane często, a jeżeli przetrwają kilka cykli, mogą zostać przeniesione do starszej generacji. Taki model jest wydajny, gdy większość obiektów szybko umiera. Problemy zaczynają się, gdy aplikacja alokuje zbyt szybko, utrzymuje zbyt wiele obiektów długowiecznych albo tworzy duże obiekty, które trudno efektywnie przenosić.

G1GC jest rozsądnym domyślnym kolektorem dla wielu aplikacji, ponieważ stara się równoważyć throughput i przewidywalność pauz. Nie oznacza to, że zawsze jest najlepszy. Dla aplikacji wymagających bardzo niskich pauz można rozważać kolektory takie jak ZGC lub Shenandoah, a dla zadań batchowych czasem ważniejszy jest throughput niż minimalna pauza. Wybór GC powinien wynikać z wymagań: czy ważniejsza jest maksymalna przepustowość, stabilna latencja, małe p99, niskie zużycie CPU, czy duże sterty.

Logi GC są podstawowym źródłem wiedzy. Powinny być włączone w produkcji, ponieważ bez nich trudno ocenić, czy GC jest realnym problemem. Analizując logi, należy patrzeć na częstotliwość kolekcji, czas pauz, ilość odzyskiwanej pamięci, promocję obiektów do starszej generacji, humongous allocations, full GC oraz relację między czasem aplikacji a czasem spędzonym w GC. Sam fakt, że GC się wykonuje, nie jest problemem. Problemem jest dopiero sytuacja, w której GC zabiera istotną część czasu, powoduje nieakceptowalne pauzy albo nie nadąża z odzyskiwaniem pamięci.

## Alokacje i presja na GC

Wydajność aplikacji Java często pogarsza się nie dlatego, że pojedyncza operacja jest wolna, ale dlatego, że wykonuje się miliony razy i generuje nadmierną liczbę obiektów. Każda alokacja jest zwykle tania, ale ich masowość zwiększa presję na GC. Dlatego warto analizować profil alokacji, szczególnie w gorących ścieżkach kodu.

Minimalizacja alokacji nie oznacza powrotu do ręcznego zarządzania pamięcią ani obsesyjnego unikania obiektów. Chodzi o unikanie alokacji niepotrzebnych. Przykładem jest tworzenie nowych obiektów w każdej iteracji gorącej pętli, nadmierne używanie boxing/unboxing, tworzenie tymczasowych kolekcji, niekontrolowane konkatenacje stringów w pętlach, kosztowne mapowanie DTO w bardzo często wykonywanych ścieżkach albo używanie `BigDecimal` tam, gdzie precyzja dziesiętna nie jest wymagana.

`StringBuilder` jest klasycznym przykładem ograniczania zbędnych obiektów przy składaniu tekstu w pętli. Trzeba jednak pamiętać, że kompilator i JVM potrafią optymalizować proste konkatenacje stringów, więc ręczne użycie `StringBuilder` nie zawsze jest potrzebne. Ma sens przede wszystkim wtedy, gdy budujemy tekst iteracyjnie, w pętli lub w miejscu intensywnie wykonywanym.

Należy również uważać na ręczne poolowanie obiektów. W starych środowiskach mogło być częściej uzasadnione, ale współczesne JVM bardzo dobrze radzą sobie z krótkotrwałymi obiektami. Ręczne pule mogą zwiększyć złożoność, pogorszyć lokalność danych, utrzymywać obiekty przy życiu zbyt długo i zwiększać presję na starszą generację. Poolowanie ma sens głównie dla naprawdę kosztownych zasobów, takich jak połączenia do bazy danych, wątki, bufory natywne albo obiekty o potwierdzonym wysokim koszcie tworzenia.

## Lokalne dane, cache CPU i struktury danych

Wydajność nie zależy wyłącznie od liczby operacji w abstrakcyjnym sensie. Współczesne procesory są bardzo szybkie, ale dostęp do pamięci bywa relatywnie kosztowny. Lokalność danych ma ogromne znaczenie. Struktury przechowujące dane w sposób ciągły w pamięci są często szybsze niż struktury rozproszone po stercie, nawet jeśli teoretyczna złożoność nie wydaje się dramatycznie różna.

`ArrayList` zwykle jest lepszym wyborem niż `LinkedList` dla większości zastosowań. `ArrayList` przechowuje referencje w tablicy, co daje dobrą lokalność i szybki dostęp indeksowy. `LinkedList` składa się z wielu osobnych węzłów, co oznacza więcej alokacji, więcej indirection, gorszą lokalność cache i większy narzut pamięci. Teoretyczna łatwość wstawiania elementów w środku listy rzadko rekompensuje praktyczne koszty, zwłaszcza jeśli najpierw trzeba dojść do miejsca wstawienia.

Dobór struktury danych powinien wynikać z profilu operacji. Jeżeli często sprawdzamy przynależność, lepszy może być `HashSet` niż lista. Jeżeli potrzebujemy uporządkowania, można rozważyć `TreeMap`, `TreeSet` albo sortowanie tablicy/listy i wyszukiwanie binarne. Jeżeli mamy bardzo dużo prymitywnych wartości liczbowych, standardowe kolekcje obiektowe mogą generować koszt boxing/unboxing i duży narzut pamięci. Wtedy warto rozważyć specjalizowane struktury danych, ale dopiero po potwierdzeniu problemu pomiarem.

## Algorytmy ważniejsze niż mikrooptymalizacje

Największe zyski wydajnościowe zwykle pochodzą ze zmiany algorytmu lub architektury przepływu danych. Jeżeli aplikacja wykonuje operację O(n²) na dużych danych, drobne poprawki w kodzie nie rozwiążą problemu. Zamiana zagnieżdżonej pętli na mapę pomocniczą, indeks, sortowanie z merge, batching albo preagregację może dać poprawę o rzędy wielkości.

Mikrooptymalizacje mają sens tylko w gorących ścieżkach, które zostały zidentyfikowane profilerem. Przykładowo unikanie niepotrzebnych alokacji, zmniejszenie liczby wywołań kosztownej metody, uproszczenie serializacji albo zmiana struktury danych może być wartościowa, jeśli dotyczy fragmentu wykonywanego miliony razy. Natomiast ręczne przepisywanie czytelnego kodu w miejscach rzadko wykonywanych zwykle pogarsza projekt bez zauważalnego zysku.

Warto rozróżniać optymalizację lokalną od systemowej. Lokalnie szybsza funkcja może pogorszyć cały system, jeżeli zwiększa zużycie pamięci, nasila GC, blokuje wątki albo komplikuje cache. Dobra optymalizacja poprawia metrykę, która ma znaczenie dla użytkownika lub systemu, na przykład czas odpowiedzi, throughput, koszt infrastruktury albo stabilność percentyli.

## Współbieżność a wydajność

Więcej wątków nie oznacza automatycznie większej wydajności. Jeżeli aplikacja jest ograniczona przez CPU, liczba aktywnych wątków znacznie większa niż liczba rdzeni może zwiększyć koszt przełączania kontekstu i pogorszyć cache locality. Jeżeli aplikacja jest ograniczona przez I/O, większa liczba wątków może pomóc ukryć czas oczekiwania, ale tylko do pewnego momentu. Zbyt duże pule wątków prowadzą do większego zużycia pamięci, większej liczby przełączeń i trudniejszych awarii pod obciążeniem.

Blokady są częstym źródłem problemów wydajnościowych. Jedna globalna blokada może serializować cały system i ograniczać skalowanie. Z drugiej strony nadmierne rozdrabnianie blokad zwiększa złożoność i ryzyko deadlocku. Wydajne programowanie współbieżne polega na ograniczaniu współdzielonego stanu, stosowaniu właściwych struktur współbieżnych oraz projektowaniu przepływu danych tak, aby minimalizować kontencję.

`ConcurrentHashMap`, `LongAdder`, kolejki blokujące i executory są zwykle lepszym wyborem niż ręczne budowanie synchronizacji od zera. `LongAdder` może być korzystniejszy niż `AtomicLong` przy dużej kontencji na liczniku, ponieważ rozprasza aktualizacje wewnętrznie i zmniejsza rywalizację o jedną komórkę pamięci. Nie jest jednak zawsze idealnym zamiennikiem, ponieważ odczyt sumy może być mniej „natychmiastowo spójny” niż pojedynczy atomik. Jak zwykle, wybór powinien wynikać z semantyki i pomiarów.

## BigDecimal, liczby i gorące pętle

`BigDecimal` jest poprawnym wyborem dla obliczeń wymagających precyzji dziesiętnej, szczególnie w finansach, rozliczeniach i domenach, gdzie błędy zaokrągleń binarnych są niedopuszczalne. Nie jest jednak typem tanim. Operacje na `BigDecimal` tworzą obiekty i są znacznie cięższe niż operacje na typach prymitywnych. Używanie `BigDecimal` w gorących pętlache bez rzeczywistej potrzeby może być poważnym źródłem kosztu.

Nie oznacza to, że należy zastępować `BigDecimal` typem `double` w logice finansowej. To byłby błąd poprawnościowy. Często lepszym rozwiązaniem jest reprezentowanie kwot jako liczby całkowitej najmniejszych jednostek, na przykład groszy lub centów, jeżeli domena na to pozwala. Inną strategią jest ograniczanie liczby operacji `BigDecimal`, przesuwanie ich poza gorące ścieżki lub agregowanie danych w bardziej efektywnym formacie, a dopiero na granicy domeny wykonywanie precyzyjnych obliczeń dziesiętnych.

Wydajność liczbowa wymaga więc rozróżnienia między poprawnością a szybkością. Szybkie, ale niepoprawne obliczenie jest gorsze niż wolniejsze poprawne. Optymalizacja typów liczbowych powinna być prowadzona tylko wtedy, gdy znamy wymagania domeny i mamy pomiar wskazujący, że koszt jest istotny.

## JMH i pułapki mikrobenchmarków

JMH jest standardowym narzędziem do mikrobenchmarków w Javie, ponieważ uwzględnia wiele pułapek związanych z JIT, rozgrzewaniem, eliminacją martwego kodu i niestabilnością pomiarów. Pisanie własnych benchmarków na podstawie `System.nanoTime()` bywa mylące, jeśli nie kontroluje się warmupu, liczby iteracji, forków, dead code elimination i efektów ubocznych.

Mikrobenchmark odpowiada na bardzo wąskie pytanie: jak zachowuje się konkretny fragment kodu w sztucznie kontrolowanych warunkach. Nie odpowiada automatycznie na pytanie, czy zmiana poprawi produkcyjny system. Fragment szybszy w izolacji może nie mieć znaczenia w aplikacji, jeżeli nie znajduje się na gorącej ścieżce. Może też pogorszyć inne aspekty, takie jak zużycie pamięci lub czytelność.

JMH warto stosować wtedy, gdy porównujemy alternatywne implementacje małego, często wykonywanego fragmentu i umiemy odtworzyć warunki zbliżone do rzeczywistego użycia. Wynik mikrobenchmarku powinien być traktowany jako dane pomocnicze, a nie ostateczny dowód. Po zmianie należy nadal mierzyć zachowanie całego systemu.

## Logowanie i wyjątki

Logowanie może być istotnym kosztem wydajnościowym, szczególnie w gorących ścieżkach lub przy dużym wolumenie zdarzeń. Samo zbudowanie komunikatu loga może generować alokacje, nawet jeśli dany poziom logowania jest wyłączony, zależnie od sposobu użycia API. Dlatego warto stosować parametryzowane logowanie i unikać kosztownych obliczeń w argumentach logów, jeżeli nie są potrzebne.

Wyjątki w Javie są przeznaczone do sytuacji wyjątkowych, a nie do sterowania zwykłym przepływem programu w gorących ścieżkach. Tworzenie wyjątku wiąże się często z kosztem wypełnienia stack trace. Jeżeli wyjątek występuje masowo jako część normalnej logiki, może stać się poważnym problemem wydajnościowym i diagnostycznym. Dodatkowo zalew logów wyjątkami utrudnia znalezienie prawdziwych awarii.

Nie należy jednak usuwać logowania ani wyjątków wyłącznie w imię wydajności. Obserwowalność jest warunkiem skutecznej diagnostyki. Celem jest rozsądny poziom logowania, właściwe próbkowanie, agregacja metryk i unikanie generowania nadmiernego szumu.

## Konfiguracja JVM i flagi

Flagi JVM są narzędziem strojenia, ale nie powinny być pierwszym miejscem optymalizacji. Wiele problemów wynika z algorytmów, I/O, bazy danych, błędnej liczby wątków albo nadmiernych alokacji. Zmiana flag GC może przesunąć objawy, ale nie rozwiąże przyczyny, jeśli aplikacja tworzy ogromne ilości niepotrzebnych obiektów albo wykonuje nieefektywne zapytania do bazy.

W produkcji warto mieć świadomie ustawione podstawowe parametry. Rozmiar sterty powinien być dostosowany do obciążenia i limitów środowiska. Logowanie GC powinno być włączone. Należy znać używany collector i rozumieć, dlaczego został wybrany. W kontenerach trzeba uwzględnić limity pamięci i CPU. Warto też zadbać o spójność konfiguracji między środowiskami, ponieważ różnice między lokalnym uruchomieniem, stagingiem i produkcją mogą utrudniać diagnozę.

Niebezpieczne jest kopiowanie zestawów flag z Internetu bez zrozumienia. Flagi mogą być przestarzałe, nie działać w danej wersji JDK, pogarszać zachowanie aplikacji albo rozwiązywać problem, którego dana aplikacja nie ma. Strojenie JVM powinno być eksperymentem kontrolowanym: jedna zmiana, pomiar przed i po, jasna hipoteza oraz możliwość wycofania.

## Diagnostyka: co sprawdzać najpierw

Pierwszym krokiem diagnostyki jest określenie objawu. Inaczej analizuje się wysokie CPU, inaczej rosnącą pamięć, inaczej długie pauzy, inaczej timeouty, a inaczej niski throughput. Następnie trzeba ustalić, czy problem jest stały, okresowy, zależny od obciążenia, związany z konkretnym endpointem, konkretnym klientem, konkretną operacją lub konkretną godziną.

Jeżeli CPU jest wysokie, należy zebrać profil CPU i sprawdzić flamegraph. Trzeba zobaczyć, gdzie aplikacja spędza czas, zamiast optymalizować podejrzane fragmenty. Jeżeli CPU jest niskie, a latencja wysoka, należy sprawdzić oczekiwanie: blokady, pule wątków, połączenia do bazy, kolejki, sieć i usługi zewnętrzne. Jeżeli występują skoki opóźnień, trzeba przeanalizować GC, safepointy, alokacje i zdarzenia systemowe.

Jeżeli pamięć rośnie, nie należy od razu zakładać memory leak. W Javie wykorzystanie sterty może rosnąć do momentu, w którym GC uzna kolekcję za potrzebną. Prawdziwy wyciek objawia się tym, że po pełnych cyklach GC pozostaje coraz więcej obiektów osiągalnych. Wtedy potrzebne są heap dumpy, analiza dominatorów i zrozumienie, kto trzyma referencje. Częste przyczyny to cache bez limitu, statyczne mapy, listenery bez wyrejestrowania, kolejki bez konsumentów lub przechowywanie dużych obiektów w kontekście żądania.

Jeżeli GC zabiera dużo czasu, trzeba sprawdzić tempo alokacji, rozmiar żywych danych, promocję do starej generacji, duże obiekty i konfigurację sterty. Czasem rozwiązaniem jest większa sterta, czasem mniejsza liczba alokacji, czasem inny collector, a czasem zmiana architektury przepływu danych. Samo zwiększanie `-Xmx` może tylko opóźnić problem, jeśli aplikacja ma realny wyciek pamięci.

## Checklist diagnostyczna

Przy problemie wydajnościowym najpierw należy zebrać dane. Sprawdź wykorzystanie CPU, pamięci, dysku i sieci. Zobacz, czy aplikacja jest ograniczona przez obliczenia, oczekiwanie, GC czy zewnętrzną usługę. Przeanalizuj logi GC i określ, czy pauzy lub czas pracy GC są istotne. Zbierz profil CPU i profil alokacji, najlepiej pod realistycznym obciążeniem. Sprawdź liczbę wątków, ich stany, blokady i kolejki. Zweryfikuj pule połączeń do bazy, timeouty, zapytania SQL i indeksy. Oceń, czy problem dotyczy całego systemu, czy pojedynczej ścieżki.

Następnie sformułuj hipotezę i wykonaj jedną zmianę naraz. Po zmianie powtórz pomiar. Jeżeli metryka się poprawiła, sprawdź, czy nie pogorszyły się inne parametry. Jeżeli nie ma poprawy, wycofaj zmianę lub pozostaw ją tylko wtedy, gdy ma niezależne uzasadnienie. Optymalizacja bez pętli pomiar–zmiana–pomiar prowadzi do przypadkowych rezultatów.

Warto pamiętać, że wiele problemów wydajnościowych leży poza JVM. Niewydajne zapytanie SQL, brak indeksu, powolna usługa zewnętrzna, przeciążona kolejka, limit CPU w kontenerze lub błędna konfiguracja load balancera mogą wyglądać jak „wolna Java”, ale nie zostaną rozwiązane przez zmianę kolektora GC.

## Kolejność optymalizacji

Rozsądna kolejność działania jest następująca. Najpierw mierzymy i klasyfikujemy problem. Następnie identyfikujemy wąskie gardło. Potem optymalizujemy algorytm, strukturę danych, zapytania, alokacje lub architekturę przepływu, zależnie od wyniku diagnozy. Dopiero później przechodzimy do strojenia JVM i flag GC, jeśli dane wskazują, że ma to sens. Na końcu weryfikujemy efekt w warunkach możliwie zbliżonych do produkcyjnych.

Ta kolejność jest ważna, ponieważ strojenie JVM nie naprawi złej złożoności algorytmu. Jeżeli system wykonuje zbyt wiele pracy, najlepszy garbage collector tylko sprawniej obsłuży skutki tej pracy. Jeżeli problemem jest baza danych, szybszy kod Javy może nie zmienić czasu odpowiedzi. Jeżeli problemem jest globalna blokada, zwiększenie liczby wątków może pogorszyć sytuację.

Najbardziej wartościowe optymalizacje często upraszczają system: usuwają zbędną pracę, zmniejszają liczbę zapytań, eliminują powtarzalne obliczenia, ograniczają alokacje, poprawiają strukturę danych albo zmieniają przepływ tak, aby unikać kontencji. Optymalizacje, które komplikują kod, powinny mieć mocne uzasadnienie pomiarowe.

## Kryterium done

Temat można uznać za opanowany, gdy potrafisz wyjaśnić, dlaczego bez profilowania nie należy rozpoczynać optymalizacji. Powinieneś umieć odróżnić problem CPU-bound od I/O-bound, problem GC od problemu alokacji oraz problem JVM od problemu bazy danych lub sieci. Powinieneś rozumieć, że wysoka średnia wydajność nie wystarcza, jeżeli percentyle ogonowe są złe.

Powinieneś znać rolę JIT, rozgrzewania aplikacji, tiered compilation i podstawowych optymalizacji HotSpota. Powinieneś rozumieć, dlaczego mikrobenchmarki wymagają JMH i dlaczego wynik mikrobenchmarku nie jest automatycznie dowodem poprawy produkcyjnej. Powinieneś umieć czytać podstawowe sygnały z logów GC i wiedzieć, kiedy GC jest objawem, a kiedy przyczyną problemu.

Na poziomie kodu powinieneś umieć wskazać typowe źródła kosztów: złą złożoność algorytmiczną, niewłaściwą strukturę danych, nadmierne alokacje, boxing, kosztowne operacje w gorących pętlach, niekontrolowane logowanie, wyjątki używane jako flow control oraz nadmierną kontencję. Na poziomie architektury powinieneś umieć ocenić wpływ bazy danych, sieci, kolejek, pul wątków, cache i zewnętrznych usług.

Najważniejsze kryterium praktyczne jest proste: każda decyzja optymalizacyjna powinna mieć hipotezę, pomiar przed zmianą, zmianę oraz pomiar po zmianie. Jeżeli nie można pokazać danych, nie można uczciwie stwierdzić, że aplikacja została zoptymalizowana.

## Podsumowanie

Wydajność aplikacji Java jest efektem świadomego projektowania, pomiaru i weryfikacji. JVM oferuje bardzo dobre mechanizmy automatyczne: JIT, adaptacyjne zarządzanie pamięcią, dojrzałe garbage collectory i bogate narzędzia diagnostyczne. Największe błędy pojawiają się wtedy, gdy programista próbuje zastąpić pomiar intuicją, a zrozumienie systemu zestawem przypadkowych flag.

Najpierw należy mierzyć. Potem ustalić, czy problem dotyczy CPU, GC, alokacji, blokad, I/O, bazy danych czy architektury. Następnie warto optymalizować algorytmy, struktury danych i przepływ pracy. Dopiero na końcu należy stroić JVM, jeśli profil i metryki wskazują, że to rzeczywiście właściwy kierunek. Zmiana z O(n²) na O(n log n), ograniczenie niepotrzebnych alokacji albo usunięcie zbędnego zapytania do bazy zwykle daje więcej niż najbardziej efektowna flaga JVM.

Dobra optymalizacja nie polega na pisaniu sprytnego kodu. Polega na usunięciu niepotrzebnej pracy, zachowaniu poprawności i potwierdzeniu efektu danymi.
