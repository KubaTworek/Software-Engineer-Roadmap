# System Design: skalowanie i odporność

## Streszczenie zarządcze

Projektowanie systemów skalowalnych i odpornych nie polega przede wszystkim na znajomości dużej liczby wzorców architektonicznych. Wzorce są użyteczne, ale dopiero wtedy, gdy wiadomo, jaki problem mają rozwiązać. Kluczową umiejętnością jest rozpoznanie, który komponent systemu jako pierwszy stanie się ograniczeniem, przy jakim poziomie ruchu to nastąpi, jak objawi się to w metrykach oraz jak można to potwierdzić eksperymentem. Architektura nie powinna być zbiorem modnych technologii, lecz hipotezą dotyczącą przepływu ruchu, pojemności poszczególnych elementów i sposobu degradacji systemu pod obciążeniem.

W tym opracowaniu przyjęto podejście neutralne względem dostawcy chmury. Nie zakładamy konkretnego budżetu, konkretnego cloud providera ani szczegółowego profilu ruchu. Oznacza to, że nacisk położony jest na teorię, zależności między komponentami oraz uniwersalne mechanizmy skalowania i odporności, a nie na konkretne usługi jednej platformy. Takie podejście jest szczególnie przydatne podczas rozmów system design, przeglądów architektury oraz planowania rozwoju systemu, ponieważ pozwala najpierw zrozumieć problem, a dopiero później dobrać narzędzia.

Najważniejsza zasada brzmi: najpierw trzeba policzyć, potem zmierzyć, a dopiero na końcu komplikować architekturę. System powinien być projektowany wokół mierzalnych ograniczeń. Dla każdego krytycznego komponentu należy umieć wskazać wzór pojemności, parametr pomiaru, aktualny limit oraz metrykę potwierdzającą, że dany komponent rzeczywiście jest lub nie jest bottleneckiem. Bez takiego modelu decyzje architektoniczne łatwo stają się intuicyjne, kosztowne i trudne do obrony.

## Fundamenty myślenia o skalowaniu

Skalowanie oznacza zdolność systemu do obsłużenia większego obciążenia przy akceptowalnym poziomie jakości usług. Nie chodzi wyłącznie o maksymalną liczbę requestów na sekundę. Równie ważne są opóźnienia, stabilność, koszt jednostkowy obsługi ruchu, przewidywalność zachowania pod presją oraz możliwość dalszego rozwoju systemu bez gwałtownego wzrostu złożoności.

W praktyce system zaczyna się skalować od najtańszego i najprostszego ruchu. Najpierw usuwa się oczywiste marnotrawstwo: niepotrzebne zapytania do bazy danych, brak cache, zbyt ciężkie odpowiedzi API, synchroniczne operacje, które mogą być asynchroniczne, oraz brak limitów na zależnościach. Dopiero później wprowadza się bardziej zaawansowane techniki, takie jak shardowanie, event-driven architecture, CQRS, wieloregionowość czy skomplikowane mechanizmy replikacji.

Dobra architektura zwykle zaczyna się od prostego modelu: stateless API, baza danych jako źródło prawdy, cache dla często odczytywanych danych, kolejka dla pracy asynchronicznej oraz obserwowalność pozwalająca sprawdzić, gdzie kończy się pojemność systemu. Taki układ jest zrozumiały, łatwy do testowania i pozwala stopniowo dokładać złożoność tam, gdzie rzeczywiście jest potrzebna.

## Skalowanie pionowe i poziome

Skalowanie pionowe polega na zwiększaniu zasobów pojedynczej instancji, na przykład dodaniu większej liczby rdzeni CPU, większej ilości pamięci RAM, szybszego dysku lub mocniejszej maszyny dla bazy danych. Jego największą zaletą jest prostota. Nie wymaga istotnej zmiany modelu programistycznego ani głębokiej przebudowy systemu. Bardzo często jest najrozsądniejszym pierwszym krokiem, szczególnie wtedy, gdy system nie osiągnął jeszcze skali, przy której złożoność skalowania poziomego jest uzasadniona.

Skalowanie pionowe ma jednak naturalne granice. W pewnym momencie większa maszyna staje się bardzo droga, trudno dostępna albo nadal nie rozwiązuje problemu pojedynczego punktu awarii. Pionowe skalowanie kupuje czas i prostotę, ale nie daje pełnej elastyczności ani odporności. Jest szczególnie przydatne dla komponentów trudnych do podziału, takich jak relacyjna baza danych przed shardowaniem, ale nie powinno być traktowane jako jedyna strategia długoterminowa.

Skalowanie poziome polega na dodawaniu kolejnych instancji komponentu. Stateless API jest klasycznym przykładem elementu dobrze skalującego się poziomo, ponieważ każda instancja może obsłużyć dowolny request, a stan użytkownika nie jest przechowywany lokalnie. Dzięki temu load balancer może rozdzielać ruch między wiele kopii serwisu, a autoscaler może zwiększać lub zmniejszać ich liczbę w zależności od obciążenia.

Poziome skalowanie zwiększa elastyczność i odporność, ale wprowadza dodatkową złożoność. Pojawiają się problemy związane z koordynacją, spójnością danych, rozproszonym cache, idempotencją operacji, równoczesnym dostępem do zasobów, limitem połączeń do bazy oraz obserwowalnością w środowisku rozproszonym. Właśnie dlatego skalowanie poziome powinno być konsekwencją jasno zidentyfikowanego problemu, a nie domyślną odpowiedzią na każdy wzrost ruchu.

## Bottleneck jako centralne pojęcie projektowania

Każdy system ma bottleneck, czyli komponent, który jako pierwszy ogranicza dalszy wzrost przepustowości. Może to być CPU aplikacji, pula połączeń HTTP, pula połączeń do bazy danych, liczba zapytań na sekundę obsługiwanych przez bazę, limit operacji cache, przepustowość sieci, kolejka, zewnętrzne API albo blokada wynikająca z modelu danych. Celem projektanta nie jest udawanie, że bottlenecków nie będzie, lecz świadome wskazanie, gdzie one występują i co stanie się po ich osiągnięciu.

Dobry projekt architektury powinien odpowiadać na pytanie: przy jakim ruchu system przestanie spełniać założenia jakościowe? Nie wystarczy stwierdzić, że system „skaluje się horyzontalnie”. Trzeba wiedzieć, czy dodanie kolejnych instancji API rzeczywiście zwiększy przepustowość, czy tylko szybciej przeciąży bazę danych. W wielu systemach aplikacja webowa nie jest najtrudniejszym problemem. Najczęściej pierwszym realnym ograniczeniem okazują się zależności: baza danych, cache, broker wiadomości, wyszukiwarka, zewnętrzna usługa płatności albo system autoryzacji.

Rozpoznanie bottlenecku wymaga połączenia modelowania i pomiarów. Model daje przybliżenie, które pozwala przewidzieć kolejność ograniczeń. Test obciążeniowy pokazuje, czy przewidywanie było poprawne. Metryki wskazują, dlaczego system zachował się w określony sposób. Dopiero połączenie tych trzech elementów daje podstawę do decyzji architektonicznej.

## Podstawowe wzory pojemności

Najbardziej użytecznym wzorem w skalowaniu systemów jest Little’s Law. W uproszczeniu mówi on, że liczba współbieżnych operacji w systemie jest równa przepustowości pomnożonej przez czas obsługi. Jeżeli system obsługuje 1000 requestów na sekundę, a średnia latencja wynosi 200 milisekund, to w danym momencie w systemie znajduje się około 200 aktywnych requestów. Ten prosty związek pozwala rozumieć, dlaczego wzrost latencji zwiększa presję na zasoby, nawet jeśli liczba requestów na sekundę pozostaje stała.

W praktyce concurrency = throughput × latency jest jednym z najważniejszych narzędzi myślenia o pojemności. Jeśli latencja rośnie, system utrzymuje więcej jednoczesnych operacji. Więcej jednoczesnych operacji oznacza więcej wątków, więcej goroutines, więcej socketów, więcej pamięci, więcej połączeń i większą presję na zależności. Dlatego degradacja często nie jest liniowa. System może przez długi czas działać stabilnie, a następnie po przekroczeniu pewnego punktu gwałtownie wejść w spiralę opóźnień.

Drugim praktycznym modelem jest limit CPU dla API. Jeśli pojedynczy request zużywa średnio określoną liczbę milisekund CPU, można oszacować maksymalną liczbę requestów obsługiwanych przez instancję. Dla uproszczenia, jeśli jedna instancja ma 4 rdzenie, to dysponuje około 4000 milisekund CPU na sekundę. Jeśli request zużywa 20 milisekund CPU, teoretyczny limit wynosi około 200 requestów na sekundę na instancję, zanim uwzględni się narzut runtime’u, garbage collection, system operacyjny, szyfrowanie, logowanie i inne koszty.

Trzecim modelem jest limit puli połączeń. Nawet jeśli API ma dużo CPU, może zostać ograniczone przez liczbę dostępnych połączeń do bazy lub zewnętrznej usługi. Jeśli instancja ma pulę 50 połączeń do bazy, a każde zapytanie trwa średnio 100 milisekund, to maksymalna przepustowość tej puli wynosi około 500 operacji na sekundę, pod warunkiem że baza jest w stanie je obsłużyć. Jeśli czas zapytania wzrośnie do 500 milisekund, ta sama pula obsłuży już tylko około 100 operacji na sekundę. Widać więc, że latencja zależności bezpośrednio zmniejsza przepustowość aplikacji.

Czwartym modelem jest limit QPS bazy danych. Baza nie jest abstrakcyjnym magazynem o nieskończonej pojemności. Każde zapytanie zużywa CPU, pamięć, I/O, locki, cache bufferów i czas planera. Proste odczyty po indeksie są tanie, ale zapytania skanujące wiele rekordów, agregacje, sortowania, transakcje z blokadami i częste zapisy mogą bardzo szybko stać się ograniczeniem. Pojemność bazy powinna być rozpatrywana nie tylko jako liczba requestów aplikacyjnych, lecz jako liczba rzeczywistych operacji bazodanowych generowanych przez jeden request.

## Latencja, throughput i „kolano” krzywej

Wydajność systemu nie powinna być oceniana wyłącznie przez średnią latencję. Średnia ukrywa problemy, które są widoczne dopiero w percentylach. W systemach produkcyjnych szczególnie istotne są p95, p99 oraz p99.9, ponieważ to one pokazują doświadczenie najwolniejszych requestów i ujawniają przeciążenia, kolejki, retry stormy oraz nierównomierne rozłożenie obciążenia.

Podczas testu obciążeniowego zwykle obserwuje się moment, w którym dalszy wzrost RPS nie powoduje proporcjonalnego wzrostu throughputu, za to powoduje gwałtowny wzrost latencji. Ten punkt często nazywa się kolanem krzywej. Przed kolanem system ma zapas pojemności. Po kolanie requesty zaczynają czekać w kolejkach, pula połączeń się zapełnia, CPU zbliża się do saturacji, garbage collection trwa dłużej, a zależności odpowiadają coraz wolniej.

Celem load testu nie jest jedynie znalezienie maksymalnego RPS. Ważniejsze jest zrozumienie, gdzie znajduje się bezpieczna strefa pracy. System powinien działać poniżej punktu saturacji, z zapasem na skoki ruchu, retry, awarie części instancji oraz nierównomierne rozłożenie requestów. Projektowanie bez zapasu prowadzi do architektury kruchej, w której niewielka zmiana profilu ruchu powoduje kaskadową degradację.

## Stateless API jako domyślny punkt startu

Stateless API jest jednym z najważniejszych wzorców skalowalności. Oznacza, że instancja aplikacji nie przechowuje lokalnie stanu wymaganego do obsługi kolejnych requestów tego samego użytkownika. Sesja, dane użytkownika, koszyk, stan procesu biznesowego albo tokeny powinny być przechowywane poza lokalną pamięcią pojedynczej instancji, na przykład w bazie, cache, storage lub tokenie przekazywanym przez klienta.

Dzięki stateless API load balancer może swobodnie kierować ruch do dowolnej instancji. Awaria jednej instancji nie oznacza utraty stanu użytkownika. Autoscaling jest prostszy, ponieważ nowa instancja może natychmiast zacząć obsługiwać requesty, a usunięcie starej nie wymaga migracji lokalnego stanu. To nie eliminuje wszystkich problemów, ale znacząco upraszcza skalowanie poziome.

Stan lokalny nie zawsze jest błędem, ale powinien być świadomą decyzją. Lokalne cache, pamięć procesu, kolejki wewnętrzne i sticky sessions mogą poprawić wydajność, lecz utrudniają równomierne rozkładanie ruchu i odporność na awarie. Jeśli system wymaga sticky sessions, należy rozumieć, co stanie się po utracie instancji oraz czy load balancer nie doprowadzi do nierównomiernego przeciążenia części workerów.

## Cache jako redukcja kosztu odczytu

Cache jest jednym z najtańszych sposobów zwiększenia pojemności systemu, ale tylko wtedy, gdy jest stosowany świadomie. Jego celem jest zmniejszenie liczby kosztownych operacji, najczęściej zapytań do bazy danych lub zewnętrznych API. Cache nie powinien być traktowany jako magiczna warstwa przyspieszająca wszystko, ponieważ wprowadza własne problemy: spójność, unieważnianie, rozgrzewanie, stampede, eviction policy oraz dodatkową zależność infrastrukturalną.

Najprostszy przypadek cache to dane często odczytywane i rzadko zmieniane. Wtedy cache może znacząco obniżyć obciążenie bazy i poprawić latencję. Im większy stosunek odczytów do zapisów i im większa tolerancja na chwilową nieświeżość danych, tym bardziej cache jest opłacalny. Dla danych silnie zmiennych, krytycznych transakcyjnie lub wymagających natychmiastowej spójności cache może być trudny i ryzykowny.

W projektowaniu cache ważne są trzy pytania. Po pierwsze, co dokładnie jest cachowane: pojedynczy rekord, wynik zapytania, fragment odpowiedzi API czy cała odpowiedź HTTP. Po drugie, jak długo dane mogą być nieświeże. Po trzecie, co stanie się w przypadku nietrafienia w cache lub awarii cache. System odporny nie powinien zakładać, że cache zawsze działa. Jeżeli cache jest tylko optymalizacją, awaria powinna pogorszyć wydajność, ale nie złamać poprawności. Jeżeli cache staje się krytycznym elementem ścieżki requestu, musi być traktowany jak pełnoprawna zależność produkcyjna.

## Baza danych jako najczęstsze ograniczenie

W wielu systemach baza danych jest pierwszym poważnym bottleneckiem. Aplikacje stateless można relatywnie łatwo powielać, ale baza danych utrzymuje stan, gwarancje spójności, indeksy, transakcje i trwałość. To sprawia, że jej skalowanie jest trudniejsze niż skalowanie warstwy API.

Pierwszym krokiem nie powinno być shardowanie, lecz optymalizacja modelu użycia bazy. Należy sprawdzić indeksy, liczbę zapytań wykonywanych przez request, problem N+1, rozmiar transakcji, poziom izolacji, blokady, koszt sortowań, agregacji i joinów oraz liczbę połączeń. Często znaczący wzrost pojemności uzyskuje się przez usunięcie kilku bardzo drogich zapytań albo zmianę sposobu pobierania danych.

Repliki odczytowe pomagają wtedy, gdy system jest ograniczony przez odczyty i może zaakceptować pewne opóźnienie replikacji. Nie rozwiązują jednak problemu zapisów, transakcji ani przeciążenia głównego węzła zapisującego. Shardowanie może zwiększyć pojemność, ale istotnie podnosi złożoność: trzeba wybrać klucz shardowania, obsłużyć zapytania między shardami, migracje danych, nierównomierny rozkład ruchu oraz operacje administracyjne.

Dlatego baza danych powinna być projektowana jako zasób ograniczony i chroniony. Aplikacja powinna mieć limity połączeń, timeouty, kontrolę retry, mechanizmy backpressure oraz metryki pokazujące liczbę zapytań, czas wykonania, locki, wykorzystanie indeksów i saturację zasobów. Dodanie instancji API bez kontroli nad ruchem do bazy może pogorszyć sytuację, ponieważ większa liczba workerów wygeneruje więcej równoległych zapytań do tego samego ograniczonego zasobu.

## Kolejki i asynchroniczność

Nie każda praca musi być wykonana synchronicznie w ścieżce requestu użytkownika. Kolejki pozwalają oddzielić przyjęcie żądania od późniejszego wykonania kosztownej operacji. Dzięki temu system może szybciej odpowiedzieć użytkownikowi, wygładzić skoki ruchu i kontrolować tempo pracy wykonywanej przez downstreamy.

Kolejka nie usuwa kosztu pracy. Ona przesuwa go w czasie i pozwala lepiej nim zarządzać. Jeżeli system przyjmuje więcej zadań, niż workery są w stanie przetworzyć, kolejka będzie rosła. Dlatego kluczowymi metrykami są długość kolejki, wiek najstarszego zadania, tempo publikowania, tempo konsumowania oraz liczba błędów i retry. Sam fakt użycia kolejki nie oznacza odporności. Źle skonfigurowana kolejka może tylko ukryć przeciążenie, aż opóźnienia staną się biznesowo nieakceptowalne.

Asynchroniczność wymaga idempotencji. Worker może przetworzyć tę samą wiadomość więcej niż raz, wiadomość może zostać ponowiona po timeoutcie, a częściowy sukces może być trudny do odróżnienia od porażki. Dlatego operacje wykonywane z kolejki powinny być projektowane tak, aby ponowne wykonanie nie powodowało błędów biznesowych, duplikatów płatności, wielokrotnego wysłania krytycznych komunikatów lub niespójności danych.

## Odporność i kontrolowana degradacja

Odporność systemu oznacza zdolność do dalszego działania mimo awarii części komponentów, przeciążenia, opóźnień zależności lub błędów infrastruktury. Nie chodzi o to, aby nic nigdy się nie psuło. To nierealistyczne. Chodzi o to, aby awarie były izolowane, wykrywalne i ograniczone w skutkach.

Podstawowymi narzędziami odporności są timeouty, retry z backoffem, circuit breaker, bulkhead, rate limiting, backpressure i fallbacki. Timeout zapobiega nieskończonemu czekaniu na zależność. Retry pomaga przy chwilowych błędach, ale bez kontroli może doprowadzić do retry stormu i pogłębić przeciążenie. Circuit breaker ogranicza liczbę prób wobec usługi, która wyraźnie nie działa. Bulkhead izoluje zasoby, aby awaria jednego typu operacji nie zabrała wszystkich workerów lub połączeń. Rate limiting chroni system przed nadmiernym ruchem, a backpressure sygnalizuje klientom lub upstreamom, że system osiągnął granicę bezpiecznej pracy.

Kontrolowana degradacja jest lepsza niż całkowita awaria. System może tymczasowo wyłączyć mniej ważne funkcje, zwrócić dane z cache, opóźnić zadania asynchroniczne, ograniczyć kosztowne endpointy albo priorytetyzować ruch krytyczny. Warunkiem jest wcześniejsze określenie, które funkcje są krytyczne, a które mogą działać w trybie ograniczonym.

## Autoscaling i jego ograniczenia

Autoscaling jest użyteczny, ale nie jest magicznym rozwiązaniem problemów wydajnościowych. Najlepiej działa dla stateless komponentów, których nowe instancje szybko startują i mogą natychmiast przejąć część ruchu. Autoscaling oparty na CPU, pamięci, RPS, długości kolejki lub custom metrics może znacząco poprawić elastyczność systemu, ale musi być dopasowany do rzeczywistego bottlenecku.

Jeżeli API jest ograniczone przez CPU, skalowanie na podstawie CPU może mieć sens. Jeżeli system jest ograniczony przez bazę danych, dokładanie instancji API może jedynie zwiększyć presję na bazę. Jeżeli problemem jest długość kolejki, autoscaling workerów może pomóc, ale tylko wtedy, gdy downstream jest w stanie przyjąć większe tempo pracy. Każda polityka autoscalingu powinna więc odpowiadać na pytanie, czy skalowany komponent rzeczywiście jest elementem ograniczającym.

Autoscaling ma również opóźnienie. Nowe instancje potrzebują czasu na uruchomienie, pobranie konfiguracji, rozgrzanie cache, przejście health checków i rozpoczęcie obsługi ruchu. Przy gwałtownych skokach obciążenia system może osiągnąć przeciążenie szybciej, niż autoscaler zdąży zareagować. Dlatego dla krytycznych systemów potrzebny jest zapas pojemności, limity ruchu i mechanizmy ochronne, a nie wyłącznie reaktywne skalowanie.

## Limity, timeouty i backpressure

System bez limitów jest systemem, który w sytuacji przeciążenia będzie degradował się chaotycznie. Limity powinny istnieć na wielu poziomach: maksymalna liczba połączeń, maksymalna liczba requestów, rozmiar payloadu, czas wykonania zapytania, liczba retry, liczba zadań w kolejce, liczba równoległych operacji wobec zależności i maksymalny czas przetwarzania.

Timeouty powinny być krótsze niż czas, po którym request i tak przestaje mieć wartość dla użytkownika lub systemu. Zbyt długie timeouty powodują kumulowanie pracy i zajmowanie zasobów. Zbyt krótkie timeouty mogą powodować fałszywe błędy i zwiększać liczbę retry. Dobre timeouty wynikają z obserwacji latencji zależności i wymagań biznesowych, a nie z przypadkowych wartości domyślnych.

Backpressure jest mechanizmem informowania upstreamu, że system nie powinien przyjmować więcej pracy w danym tempie. Może mieć formę błędów 429, ograniczenia kolejki, odrzucania mniej ważnych zadań, zmniejszenia concurrency albo dynamicznego throttlingu. Bez backpressure system często zachowuje się jak zbiornik bez zaworu bezpieczeństwa: przyjmuje pracę szybciej, niż jest w stanie ją wykonać, aż dochodzi do awarii kaskadowej.

## Obserwowalność jako warunek skalowania

Nie da się odpowiedzialnie skalować systemu, którego zachowania nie można zmierzyć. Obserwowalność obejmuje metryki, logi i trace’y, ale jej celem nie jest samo zbieranie danych. Celem jest możliwość odpowiedzi na pytania: gdzie system spędza czas, który komponent jest przeciążony, jaki jest koszt pojedynczego requestu, które endpointy generują największe obciążenie, czy błędy pochodzą z aplikacji czy z zależności oraz jak zmieniają się percentyle latencji pod obciążeniem.

Podstawowe metryki warstwy API to RPS, p50, p95, p99 latency, error rate, saturacja CPU, pamięć, liczba aktywnych requestów, liczba połączeń i czas oczekiwania na zależności. Dla bazy ważne są QPS, czas zapytań, liczba aktywnych połączeń, locki, cache hit ratio, wykorzystanie indeksów, I/O i replikacja. Dla kolejki istotne są publish rate, consume rate, backlog, age of oldest message i retry count. Dla cache ważne są hit ratio, latency, eviction, memory usage i błędy połączeń.

Trace’y są szczególnie ważne w systemach rozproszonych, ponieważ pozwalają zobaczyć pełną ścieżkę requestu przez wiele usług. Bez trace’ów łatwo błędnie przypisać problem do komponentu, który tylko objawia opóźnienie, ale nie jest jego źródłem. Dobra obserwowalność skraca czas diagnozy i pozwala potwierdzić, czy zmiana architektoniczna rzeczywiście poprawiła system.

## Testy obciążeniowe i walidacja modelu

Test obciążeniowy powinien służyć walidacji hipotezy, a nie tylko wygenerowaniu dużej liczby requestów. Przed testem należy określić oczekiwania: który komponent powinien stać się bottleneckiem, przy jakim poziomie ruchu, jakie metryki to potwierdzą i gdzie powinno pojawić się kolano krzywej latencji. Po teście należy porównać wynik z modelem.

Jeżeli load test pokazuje inny bottleneck niż oczekiwany, nie jest to porażka. To cenna informacja, że model był niepełny. Być może pominięto limit połączeń, koszt serializacji JSON, narzut TLS, lock w aplikacji, problem N+1, limit zewnętrznego API albo nierównomierny rozkład danych. Największą wartością testów obciążeniowych jest właśnie ujawnianie ukrytych założeń.

Test powinien przypominać realny profil ruchu. Samo testowanie jednego prostego endpointu może dawać fałszywe poczucie bezpieczeństwa, jeśli w produkcji ruch składa się z wielu ścieżek o różnym koszcie. Ważne jest też uwzględnienie ramp-upu, stabilnego okresu testu, testu przeciążeniowego, testu powrotu do normalnego ruchu i obserwacji, czy system sam wraca do zdrowia po ustaniu presji.

## Spójność, dostępność i kompromisy

W systemach rozproszonych nie da się uniknąć kompromisów między spójnością, dostępnością i opóźnieniem. Im więcej replik, regionów, kolejek i cache, tym większe znaczenie ma pytanie, jak świeże muszą być dane i co oznacza poprawność z punktu widzenia biznesu. Nie wszystkie dane wymagają takiego samego poziomu spójności. Saldo konta, płatność i stan zamówienia zwykle wymagają większej ostrożności niż licznik wyświetleń, rekomendacje albo lista ostatnio oglądanych produktów.

Projekt powinien rozróżniać silną spójność od eventual consistency. Silna spójność upraszcza rozumowanie, ale może ograniczać skalowalność i dostępność. Eventual consistency pozwala lepiej skalować system, ale wymaga obsługi stanów pośrednich, duplikatów, opóźnień i konfliktów. Decyzja nie powinna być ideologiczna. Powinna wynikać z wymagań biznesowych i konsekwencji błędu.

## Wieloregionowość i odporność geograficzna

Wieloregionowość jest jednym z najbardziej kosztownych i złożonych sposobów zwiększania odporności. Może chronić przed awarią regionu, zmniejszać latencję dla użytkowników globalnych i poprawiać dostępność, ale wymaga rozwiązania problemów replikacji danych, routingu ruchu, failoveru, spójności, testów disaster recovery i operacyjnej gotowości zespołu.

Nie każdy system potrzebuje aktywnej pracy w wielu regionach. Czasem wystarczający jest backup, plan odtworzenia i cold standby. Czasem potrzebny jest warm standby, a czasem aktywna konfiguracja active-active. Im wyższe wymagania RTO i RPO, tym większy koszt i złożoność. RTO określa, jak szybko system musi wrócić po awarii. RPO określa, ile danych można maksymalnie utracić. Te wartości powinny wynikać z wymagań biznesowych, a nie z ambicji technologicznej.

Wieloregionowość bez regularnych testów failoveru jest często złudzeniem odporności. Jeśli procedura przełączenia nie była ćwiczona, prawdopodobnie nie zadziała poprawnie w realnej awarii. Odporność nie jest stanem deklarowanym w diagramie, lecz właściwością regularnie testowanego systemu.

## Bezpieczna kolejność komplikowania architektury

Najbezpieczniejsza ścieżka rozwoju architektury prowadzi od prostoty do złożoności. Najpierw warto mieć poprawny model domeny, stateless API, sensowne indeksy w bazie, podstawowy cache, obserwowalność, limity i testy obciążeniowe. Następnie można wprowadzać asynchroniczność, read replicas, bardziej zaawansowany cache, oddzielenie ścieżek odczytu i zapisu, partycjonowanie danych oraz niezależne skalowanie usług.

Bardziej zaawansowane wzorce, takie jak mikroserwisy, CQRS, event sourcing, shardowanie czy active-active multi-region, powinny pojawiać się wtedy, gdy rozwiązują konkretny problem. Każdy z tych wzorców ma koszt poznawczy, operacyjny i organizacyjny. Mikroserwisy mogą pomóc niezależnie skalować zespoły i komponenty, ale utrudniają transakcje, testowanie, debugging i obserwowalność. Event sourcing daje pełną historię zmian, ale komplikuje model odczytu i migracje. Shardowanie zwiększa pojemność, ale utrudnia zapytania globalne i operacje administracyjne.

Zasadą praktyczną jest komplikowanie architektury dopiero wtedy, gdy prostsze mechanizmy zostały wyczerpane albo ich ograniczenia są dobrze rozumiane. Architektura powinna rosnąć razem z problemem, nie przed nim.

## Kryterium gotowości projektu

Projekt systemu można uznać za dobrze przemyślany, jeśli dla każdego krytycznego komponentu da się wypełnić tabelę: komponent, wzór pojemności, parametr pomiaru, aktualny limit i metryka potwierdzająca. Dla API może to być limit CPU, liczba instancji, średni koszt requestu i p95 latency. Dla bazy może to być QPS, czas zapytań, liczba połączeń i wykorzystanie indeksów. Dla cache może to być hit ratio, latency i eviction rate. Dla kolejki może to być tempo publikowania, tempo konsumowania i wiek najstarszej wiadomości.

Drugim kryterium jest zgodność modelu z testem. Wynik load testu nie musi idealnie pokrywać się z obliczeniami, ale powinien zgadzać się co do kolejności bottlenecków i przybliżonego punktu, w którym p95 latency zaczyna gwałtownie rosnąć. Jeśli model mówi, że najpierw ograniczeniem będzie baza, a test pokazuje saturację CPU w API, należy wyjaśnić różnicę. Jeśli test pokazuje gwałtowny wzrost błędów bez wzrostu CPU, trzeba sprawdzić pule połączeń, timeouty, kolejki, limity zależności albo locki.

Trzecim kryterium jest kontrolowana degradacja. System powinien mieć jasną odpowiedź na pytanie, co stanie się po przekroczeniu pojemności. Czy requesty będą odrzucane szybko i jawnie? Czy kolejka zacznie rosnąć? Czy mniej ważne funkcje zostaną ograniczone? Czy retry pogorszą sytuację? Czy alerty wskażą właściwy komponent? Dobrze zaprojektowany system nie tylko działa przy normalnym ruchu, ale również przewidywalnie zachowuje się w warunkach przeciążenia.

## Podsumowanie

Skalowanie i odporność są przede wszystkim dyscypliną myślenia o ograniczeniach. Najważniejsze pytanie nie brzmi, jakich technologii użyć, ale który komponent stanie się pierwszy problemem, dlaczego, przy jakim ruchu i jak to zmierzyć. Dopiero po takiej analizie można świadomie dobrać cache, kolejki, autoscaling, repliki, shardowanie, wieloregionowość albo bardziej zaawansowane wzorce architektoniczne.

Najlepsze systemy nie są od razu najbardziej skomplikowane. Są proste tam, gdzie prostota wystarcza, i złożone tylko tam, gdzie złożoność jest uzasadniona. Skalowanie pionowe daje czas i prostotę. Skalowanie poziome daje elastyczność i odporność, ale wymaga stateless komponentów, limitów, obserwowalności i kontroli zależności. Cache redukuje koszt odczytu, ale wprowadza problem spójności. Kolejki wygładzają ruch, ale nie usuwają kosztu pracy. Autoscaling pomaga, ale tylko wtedy, gdy skaluje właściwy komponent.

Ostatecznie dojrzałość system design polega na zdolności połączenia teorii, pomiarów i decyzji architektonicznych. Jeżeli potrafimy policzyć pojemność, wskazać bottleneck, potwierdzić go testem i zaplanować bezpieczną degradację, to mamy solidną podstawę do budowy systemu skalowalnego i odpornego.
